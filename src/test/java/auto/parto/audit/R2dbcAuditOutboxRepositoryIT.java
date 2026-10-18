package auto.parto.audit;

import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static io.r2dbc.spi.ConnectionFactoryOptions.DATABASE;
import static io.r2dbc.spi.ConnectionFactoryOptions.DRIVER;
import static io.r2dbc.spi.ConnectionFactoryOptions.HOST;
import static io.r2dbc.spi.ConnectionFactoryOptions.PASSWORD;
import static io.r2dbc.spi.ConnectionFactoryOptions.PORT;
import static io.r2dbc.spi.ConnectionFactoryOptions.USER;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class R2dbcAuditOutboxRepositoryIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private DatabaseClient databaseClient;
    private R2dbcAuditOutboxRepository repository;
    private TransactionalOperator transactionalOperator;

    @BeforeAll
    void migrateAndCreateRepository() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        ConnectionFactory connectionFactory = io.r2dbc.spi.ConnectionFactories.get(
                ConnectionFactoryOptions.builder()
                        .option(DRIVER, "postgresql")
                        .option(HOST, POSTGRES.getHost())
                        .option(PORT, POSTGRES.getFirstMappedPort())
                        .option(USER, POSTGRES.getUsername())
                        .option(PASSWORD, POSTGRES.getPassword())
                        .option(DATABASE, POSTGRES.getDatabaseName())
                        .build());
        databaseClient = DatabaseClient.create(connectionFactory);
        repository = new R2dbcAuditOutboxRepository(databaseClient);
        transactionalOperator = TransactionalOperator.create(new R2dbcTransactionManager(connectionFactory));
    }

    @Test
    void claimsAndPersistsAuditDeliveryStateReactively() {
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now().minusSeconds(1);
        databaseClient.sql("""
                        INSERT INTO outbox_events (event_id, event_type, aggregate_type, payload, available_at)
                        VALUES (:eventId, 'USER_UPDATED', 'app_user', '{}'::jsonb, :availableAt)
                        """)
                .bind("eventId", eventId)
                .bind("availableAt", now)
                .fetch().rowsUpdated()
                .then()
                .thenMany(repository.claimDue("worker-1", 10, now, Duration.ofSeconds(30)))
                .as(StepVerifier::create)
                .assertNext(record -> {
                    assertThat(record.destination()).isEqualTo("AUDIT");
                    assertThat(record.event().eventId()).isEqualTo(eventId);
                    assertThat(record.event().payloadJson()).isEqualTo("{}");
                    assertThat(record.attemptCount()).isEqualTo(1);
                    assertThat(record.claimOwner()).startsWith("worker-1:");
                })
                .verifyComplete();

        UUID deliveryId = databaseClient.sql("SELECT public_id FROM outbox_deliveries WHERE outbox_event_id = "
                        + "(SELECT id FROM outbox_events WHERE event_id = :eventId)")
                .bind("eventId", eventId)
                .map((row, metadata) -> row.get("public_id", UUID.class))
                .one()
                .block();

        String claimOwner = databaseClient.sql("SELECT lease_owner FROM outbox_deliveries WHERE public_id = :deliveryId")
                .bind("deliveryId", deliveryId)
                .map((row, metadata) -> row.get("lease_owner", String.class))
                .one()
                .block();

        repository.markDelivered(deliveryId, claimOwner, now.plusSeconds(1)).block();

        String status = databaseClient.sql("SELECT status FROM outbox_deliveries WHERE public_id = :deliveryId")
                .bind("deliveryId", deliveryId)
                .map((row, metadata) -> row.get("status", String.class))
                .one()
                .block();
        assertThat(status).isEqualTo("DISPATCHED");

        StepVerifier.create(repository.findByEventId(UUID.randomUUID()))
                .verifyComplete();
    }

    @Test
    void concurrentWorkersClaimDifferentRowsAndStaleOwnerCannotCompleteReclaimedRow() {
        Instant now = Instant.now().minusSeconds(1);
        UUID firstEvent = insertPendingEvent(now);
        UUID secondEvent = insertPendingEvent(now);

        var claimed = Flux.merge(
                        repository.claimDue("worker-a", 1, now, Duration.ofSeconds(1)),
                        repository.claimDue("worker-b", 1, now, Duration.ofSeconds(1)))
                .collectList()
                .block();

        assertThat(claimed).hasSize(2);
        assertThat(claimed).extracting(record -> record.event().eventId())
                .containsExactlyInAnyOrder(firstEvent, secondEvent);

        AuditOutboxRecord first = claimed.getFirst();
        Instant afterExpiry = Instant.now().plusSeconds(2);
        AuditOutboxRecord reclaimed = repository.claimDue("worker-c", 2, afterExpiry, Duration.ofMinutes(1))
                .filter(record -> record.deliveryId().equals(first.deliveryId()))
                .blockFirst();

        assertThat(reclaimed.deliveryId()).isEqualTo(first.deliveryId());
        StepVerifier.create(repository.markDelivered(first.deliveryId(), first.claimOwner(), Instant.now()))
                .expectError(IllegalStateException.class)
                .verify();
        repository.markDelivered(reclaimed.deliveryId(), reclaimed.claimOwner(), Instant.now()).block();

        insertPendingEvent(Instant.now());
        AuditOutboxRecord retryRecord = repository.claimDue(
                        "worker-retry", 1, Instant.now(), Duration.ofMinutes(1))
                .blockFirst();
        repository.scheduleRetry(
                        retryRecord.deliveryId(), retryRecord.claimOwner(), Instant.now().minusSeconds(1),
                        "AUDIT_DELIVERY_RETRYABLE")
                .block();
        String retryStatus = databaseClient.sql(
                        "SELECT status FROM outbox_deliveries WHERE public_id = :deliveryId")
                .bind("deliveryId", retryRecord.deliveryId())
                .map((row, metadata) -> row.get("status", String.class))
                .one()
                .block();
        assertThat(retryStatus).isEqualTo("PENDING");
    }

    @Test
    void replayClaimRejectsActiveLeaseAndOnlyClaimsRequestedDelivery() {
        Instant now = Instant.now().minusSeconds(1);
        UUID activeEvent = insertPendingEvent(now);
        AuditOutboxRecord active = repository.claimDue(
                        "active-worker", 1, now, Duration.ofMinutes(1))
                .blockFirst();
        UUID unrelatedEvent = insertPendingEvent(now);
        UUID unrelatedDeliveryId = deliveryIdForEvent(unrelatedEvent);

        StepVerifier.create(repository.claimForReplay(
                        active.deliveryId(), "replay-worker", now, Duration.ofMinutes(1)))
                .verifyComplete();

        AuditOutboxRecord replayed = repository.claimForReplay(
                        unrelatedDeliveryId, "replay-worker", now, Duration.ofMinutes(1))
                .block();
        assertThat(replayed.event().eventId()).isEqualTo(unrelatedEvent);
        assertThat(replayed.event().eventId()).isNotEqualTo(activeEvent);

        String activeOwner = databaseClient.sql(
                        "SELECT lease_owner FROM outbox_deliveries WHERE public_id = :deliveryId")
                .bind("deliveryId", active.deliveryId())
                .map((row, metadata) -> row.get("lease_owner", String.class))
                .one()
                .block();
        assertThat(activeOwner).isEqualTo(active.claimOwner());
    }

    @Test
    void purgesOnlyBoundedTerminalAuditDeliveries() {
        UUID eventId = insertPendingEvent(Instant.now().minusSeconds(10));
        AuditOutboxRecord claimed = repository.claimForReplay(
                        deliveryIdForEvent(eventId), "purge-worker", Instant.now(), Duration.ofMinutes(1))
                .block();
        repository.markDelivered(claimed.deliveryId(), claimed.claimOwner(), Instant.now()).block();
        UUID pendingEventId = insertPendingEvent(Instant.now());
        long before = countAuditDeliveries();

        int purged = repository.purgeTerminalBefore(Instant.now().plusSeconds(1), 1).block();

        assertThat(purged).isEqualTo(1);
        assertThat(countAuditDeliveries()).isEqualTo(before - 1);
        assertThat(countDeliveriesForEvent(pendingEventId)).isEqualTo(1);
    }

    @Test
    void reactiveTransactionRollsBackBusinessOutboxAndDeliveryWritesTogether() {
        UUID userPublicId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        String email = "rollback-" + UUID.randomUUID() + "@example.test";

        StepVerifier.create(transactionalOperator.execute(status ->
                        databaseClient.sql("INSERT INTO app_users (public_id, email, role) "
                                        + "VALUES (:publicId, :email, 'BUYER')")
                                .bind("publicId", userPublicId)
                                .bind("email", email)
                                .fetch().rowsUpdated()
                                .then(databaseClient.sql("INSERT INTO outbox_events "
                                                + "(event_id, event_type, aggregate_type, payload) "
                                                + "VALUES (:eventId, 'ROLLBACK_TEST', 'test', '{}'::jsonb)")
                                        .bind("eventId", eventId)
                                        .fetch().rowsUpdated())
                                .then(Mono.error(new IllegalStateException("forced rollback")))
                ).then())
                .expectErrorMessage("forced rollback")
                .verify();

        assertThat(count("app_users", "public_id", userPublicId)).isZero();
        assertThat(count("outbox_events", "event_id", eventId)).isZero();
        assertThat(countDeliveriesForEvent(eventId)).isZero();
    }

    private UUID insertPendingEvent(Instant availableAt) {
        UUID eventId = UUID.randomUUID();
        databaseClient.sql("""
                        INSERT INTO outbox_events (event_id, event_type, aggregate_type, payload, available_at)
                        VALUES (:eventId, 'CONCURRENT_TEST', 'test', '{}'::jsonb, :availableAt)
                        """)
                .bind("eventId", eventId)
                .bind("availableAt", availableAt)
                .fetch().rowsUpdated()
                .block();
        return eventId;
    }

    private UUID deliveryIdForEvent(UUID eventId) {
        return databaseClient.sql("SELECT od.public_id FROM outbox_deliveries od "
                        + "JOIN outbox_events oe ON oe.id = od.outbox_event_id WHERE oe.event_id = :eventId")
                .bind("eventId", eventId)
                .map((row, metadata) -> row.get("public_id", UUID.class))
                .one()
                .block();
    }

    private long count(String table, String column, UUID value) {
        return databaseClient.sql("SELECT count(*) AS total FROM " + table + " WHERE " + column + " = :value")
                .bind("value", value)
                .map((row, metadata) -> row.get("total", Long.class))
                .one()
                .block();
    }

    private long countDeliveriesForEvent(UUID eventId) {
        return databaseClient.sql("SELECT count(*) AS total FROM outbox_deliveries od "
                        + "JOIN outbox_events oe ON oe.id = od.outbox_event_id WHERE oe.event_id = :eventId")
                .bind("eventId", eventId)
                .map((row, metadata) -> row.get("total", Long.class))
                .one()
                .block();
    }

    private long countAuditDeliveries() {
        return databaseClient.sql("SELECT count(*) AS total FROM outbox_deliveries WHERE destination = 'AUDIT'")
                .map((row, metadata) -> row.get("total", Long.class))
                .one()
                .block();
    }
}
