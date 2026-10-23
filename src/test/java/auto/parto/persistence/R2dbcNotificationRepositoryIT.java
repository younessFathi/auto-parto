package auto.parto.persistence;

import io.r2dbc.spi.ConnectionFactoryOptions;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.time.Duration;
import java.time.Instant;

import static io.r2dbc.spi.ConnectionFactoryOptions.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class R2dbcNotificationRepositoryIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private DatabaseClient databaseClient;
    private NotificationRepository repository;

    @BeforeAll
    void migrateAndCreateRepository() {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").load().migrate();
        var connectionFactory = io.r2dbc.spi.ConnectionFactories.get(ConnectionFactoryOptions.builder()
                .option(DRIVER, "postgresql").option(HOST, POSTGRES.getHost()).option(PORT, POSTGRES.getFirstMappedPort())
                .option(USER, POSTGRES.getUsername()).option(PASSWORD, POSTGRES.getPassword())
                .option(DATABASE, POSTGRES.getDatabaseName()).build());
        databaseClient = DatabaseClient.create(connectionFactory);
        repository = new R2dbcNotificationRepository(databaseClient,
                TransactionalOperator.create(new R2dbcTransactionManager(connectionFactory)));
    }

    @Test
    void enqueuesAndListsPendingNotification() {
        UUID owner = UUID.randomUUID();
        StepVerifier.create(insertUser(owner)
                        .then(repository.enqueue(owner, "OFFER_CREATED", "{\"offerId\":\"abc\"}")))
                .assertNext(notification -> {
                    assertThat(notification.recipientUserPublicId()).isEqualTo(owner);
                    assertThat(notification.eventType()).isEqualTo("OFFER_CREATED");
                    assertThat(notification.payloadJson()).contains("offerId");
                    assertThat(notification.status()).isEqualTo("PENDING");
                }).verifyComplete();
    }

    @Test
    void enqueueCommitsNotificationAndVersionedOutboxEventTogether() {
        UUID owner = UUID.randomUUID();
        StepVerifier.create(insertUser(owner)
                        .then(repository.enqueue(owner, "OFFER_CREATED", "{\"offerId\":\"abc\"}"))
                        .flatMap(notification -> databaseClient.sql("""
                                        SELECT event_type, schema_version, aggregate_public_id,
                                               payload::text AS payload, count(delivery.id) AS delivery_count
                                        FROM outbox_events event
                                        JOIN outbox_deliveries delivery ON delivery.outbox_event_id = event.id
                                        WHERE event.aggregate_public_id = :notificationId
                                        GROUP BY event.event_type, event.schema_version,
                                                 event.aggregate_public_id, event.payload
                                        """)
                                .bind("notificationId", notification.publicId())
                                .map((row, metadata) -> new OutboxSnapshot(
                                        row.get("event_type", String.class),
                                        row.get("schema_version", Integer.class),
                                        row.get("aggregate_public_id", UUID.class),
                                        row.get("payload", String.class),
                                        row.get("delivery_count", Long.class)))
                                .one()))
                .assertNext(snapshot -> {
                    assertThat(snapshot.eventType()).isEqualTo("notification.requested");
                    assertThat(snapshot.schemaVersion()).isEqualTo(1);
                    assertThat(snapshot.aggregatePublicId()).isNotNull();
                    assertThat(snapshot.payload()).contains("notificationPayload", "offerId");
                    assertThat(snapshot.deliveryCount()).isEqualTo(1L);
                }).verifyComplete();
    }

    @Test
    void statusUpdateIsIdempotentAndPendingReadsAreBounded() {
        UUID owner = UUID.randomUUID();
        StepVerifier.create(insertUser(owner)
                        .then(repository.enqueue(owner, "TEST", "{}"))
                        .flatMap(notification -> repository.updateStatus(notification.publicId(), "DISPATCHED")
                                .zipWith(repository.updateStatus(notification.publicId(), "DISPATCHED"))))
                .assertNext(result -> assertThat(result.getT1()).isTrue())
                .verifyComplete();
        StepVerifier.create(repository.findPending(100)
                        .filter(notification -> notification.eventType().equals("TEST")))
                .expectNextCount(0).verifyComplete();
    }

    @Test
    void missingRecipientDoesNotCreateNotification() {
        StepVerifier.create(repository.enqueue(UUID.randomUUID(), "TEST", "{}"))
                .verifyComplete();
    }

    @Test
    void rejectsPayloadsLargerThanTheNotificationBoundary() {
        String oversizedPayload = "{\"message\":\"" + "x".repeat(NotificationPayloadLimits.MAX_SERIALIZED_BYTES)
                + "\"}";

        assertThatThrownBy(() -> repository.enqueue(UUID.randomUUID(), "TEST", oversizedPayload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("notification payload exceeds the bounded size");
    }

    @Test
    void outboxFailureRollsBackNotificationAndEvent() {
        UUID owner = UUID.randomUUID();
        insertUser(owner).block();
        createFailingOutboxTrigger().block();
        try {
            StepVerifier.create(repository.enqueue(owner, "ROLLBACK_TEST", "{}"))
                .expectErrorMatches(error -> error.getMessage() != null
                        && error.getMessage().contains("forced notification outbox failure"))
                    .verify();
        } finally {
            dropFailingOutboxTrigger().block();
        }

        assertThat(countNotifications(owner, "ROLLBACK_TEST")).isZero();
        assertThat(countOutboxEvents("notification.requested", "ROLLBACK_TEST")).isZero();
    }

    @Test
    void recordsDeliveryAttemptForExistingNotification() {
        UUID owner = UUID.randomUUID();
        StepVerifier.create(insertUser(owner)
                        .then(repository.enqueue(owner, "TEST", "{}"))
                        .flatMap(notification -> repository.recordAttempt(notification.publicId(), "FCM", "ACCEPTED",
                                "provider-1", "")))
                .assertNext(attempt -> {
                    assertThat(attempt.channel()).isEqualTo("FCM");
                    assertThat(attempt.status()).isEqualTo("ACCEPTED");
                    assertThat(attempt.providerReference()).isEqualTo("provider-1");
                    assertThat(attempt.completedAt()).isNotNull();
                }).verifyComplete();
    }

    @Test
    void readsAndMarksOnlyTheRecipientPendingNotifications() {
        UUID owner = UUID.randomUUID();
        UUID otherOwner = UUID.randomUUID();
        StepVerifier.create(insertUser(owner)
                        .then(insertUser(otherOwner))
                        .then(repository.enqueue(owner, "OWNER_EVENT", "{}"))
                        .then(repository.enqueue(otherOwner, "OTHER_EVENT", "{}"))
                        .thenMany(repository.findPendingForRecipient(owner, 100).map(NotificationRecord::eventType).collectList())
                        .flatMap(events -> repository.markPendingForRecipient(owner).map(marked -> java.util.Map.entry(events, marked))))
                .assertNext(result -> {
                    assertThat(result.getKey()).containsExactly("OWNER_EVENT");
                    assertThat(result.getValue()).isEqualTo(1L);
                }).verifyComplete();
        StepVerifier.create(repository.findPending(100)
                        .filter(notification -> notification.eventType().equals("OWNER_EVENT")))
                .expectNextCount(0).verifyComplete();
        StepVerifier.create(repository.findPending(100)
                        .filter(notification -> notification.eventType().equals("OTHER_EVENT")))
                .expectNextCount(1).verifyComplete();
    }

    @Test
    void replaysAfterCursorWithStableUuidTieBreaker() {
        UUID owner = UUID.randomUUID();
        insertUser(owner).then(repository.enqueue(owner, "REPLAY_ONE", "{}"))
                .then(repository.enqueue(owner, "REPLAY_TWO", "{}"))
                .block();
        var records = repository.findPendingForRecipient(owner, 100)
                .filter(record -> record.eventType().startsWith("REPLAY_"))
                .collectList().block();
        records.sort(java.util.Comparator.comparing(NotificationRecord::publicId));
        databaseClient.sql("UPDATE notifications SET created_at = TIMESTAMP '2020-01-01 00:00:00+00' WHERE event_type LIKE 'REPLAY_%'")
                .fetch().rowsUpdated().block();
        records = repository.findPendingForRecipient(owner, 100)
                .filter(record -> record.eventType().startsWith("REPLAY_"))
                .collectList().block();
        records.sort(java.util.Comparator.comparing(NotificationRecord::publicId));

        var actual = repository.findForRecipientAfter(owner, records.getFirst().publicId(), 100)
                .map(NotificationRecord::publicId).collectList().block();
        assertThat(actual).containsExactlyElementsOf(records.subList(1, records.size()).stream()
                .map(NotificationRecord::publicId).toList());
    }

    @Test
    void marksOnlyTheDeliveredNotificationId() {
        UUID owner = UUID.randomUUID();
        StepVerifier.create(insertUser(owner)
                        .then(repository.enqueue(owner, "FIRST", "{\"id\":\"first\"}"))
                        .then(repository.enqueue(owner, "SECOND", "{\"id\":\"second\"}"))
                        .then(repository.markPendingForRecipientAndId(owner, "first")))
                .expectNext(true).verifyComplete();
        StepVerifier.create(repository.findPendingForRecipient(owner, 100)
                        .map(NotificationRecord::eventType))
                .expectNext("SECOND").verifyComplete();
    }

    @Test
    void countsPendingRecordsAndReturnsBoundedRecipientIds() {
        UUID owner = UUID.randomUUID();
        StepVerifier.create(insertUser(owner)
                        .then(repository.enqueue(owner, "STATS", "{}"))
                        .then(repository.countPending()))
                .assertNext(count -> assertThat(count).isGreaterThanOrEqualTo(1L))
                .verifyComplete();
        StepVerifier.create(repository.findPendingRecipientIds(100).filter(owner::equals))
                .expectNext(owner).verifyComplete();
    }

    @Test
    void concurrentClaimsHaveOneWinnerAndLeaseExpiryAllowsRecovery() {
        UUID owner = UUID.randomUUID();
        StepVerifier.create(insertUser(owner)
                        .then(repository.enqueue(owner, "CLAIM", "{}"))
                        .flatMap(notification -> {
                            Instant now = Instant.now().plusSeconds(1);
                            return Mono.zip(
                                repository.claimPendingForRecipient(owner, "worker-1", now,
                                        Duration.ofMinutes(1), 1).collectList(),
                                repository.claimPendingForRecipient(owner, "worker-2", now,
                                        Duration.ofMinutes(1), 1).collectList());
                        }))
                .assertNext(result -> assertThat(result.getT1().size() + result.getT2().size()).isEqualTo(1))
                .verifyComplete();

        StepVerifier.create(repository.claimPendingForRecipient(owner, "worker-2", Instant.now().plusSeconds(120),
                                Duration.ofMinutes(1), 1))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void retryStateIsDurableAndReachesTerminalExhaustion() {
        UUID owner = UUID.randomUUID();
        StepVerifier.create(insertUser(owner)
                        .then(repository.enqueue(owner, "RETRY", "{}"))
                        .flatMap(notification -> repository.claimPendingForRecipient(owner, "worker-retry",
                                        Instant.now(), Duration.ofMinutes(1), 1)
                                .single()
                                .flatMap(claimed -> repository.scheduleRetry(claimed.publicId(), "worker-retry",
                                        Instant.now().plusSeconds(30), "DELIVERY_UNAVAILABLE", 1))))
                .expectNext(true)
                .verifyComplete();

        StepVerifier.create(databaseClient.sql("""
                        SELECT status, attempt_count, last_failure_code, lease_owner
                        FROM notifications WHERE event_type = 'RETRY'
                        ORDER BY id DESC LIMIT 1
                        """)
                        .map((row, metadata) -> java.util.List.of(row.get("status", String.class),
                                row.get("attempt_count", Integer.class), row.get("last_failure_code", String.class)))
                        .one())
                .assertNext(row -> assertThat(row).containsExactly("EXHAUSTED", 1,
                        "DELIVERY_UNAVAILABLE"))
                .verifyComplete();
    }

    @Test
    void claimLimitIsBounded() {
        assertThatThrownBy(() -> repository.claimPendingForRecipient(UUID.randomUUID(), "worker", Instant.now(),
                Duration.ofSeconds(1), 101))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("limit must be between 1 and 100");
    }

    private reactor.core.publisher.Mono<Void> insertUser(UUID publicId) {
        return databaseClient.sql("""
                        INSERT INTO app_users (public_id, email, role, status)
                        VALUES (:publicId, :email, 'BUYER', 'ACTIVE')
                        """)
                .bind("publicId", publicId).bind("email", "notification-" + publicId + "@example.test")
                .fetch().rowsUpdated().then();
    }

    private reactor.core.publisher.Mono<Void> createFailingOutboxTrigger() {
        return databaseClient.sql("""
                        CREATE FUNCTION test_fail_notification_outbox() RETURNS trigger
                        LANGUAGE plpgsql AS $$
                        BEGIN
                            RAISE EXCEPTION 'forced notification outbox failure';
                        END;
                        $$;
                        """).fetch().rowsUpdated().then(databaseClient.sql("""
                        CREATE TRIGGER test_fail_notification_outbox_trigger
                        BEFORE INSERT ON outbox_events
                        FOR EACH ROW WHEN (NEW.event_type = 'notification.requested')
                        EXECUTE FUNCTION test_fail_notification_outbox();
                        """).fetch().rowsUpdated().then()).then();
    }

    private reactor.core.publisher.Mono<Void> dropFailingOutboxTrigger() {
        return databaseClient.sql("DROP TRIGGER test_fail_notification_outbox_trigger ON outbox_events")
                .fetch().rowsUpdated()
                .then(databaseClient.sql("DROP FUNCTION test_fail_notification_outbox()")
                        .fetch().rowsUpdated())
                .then();
    }

    private long countNotifications(UUID owner, String eventType) {
        return databaseClient.sql("""
                        SELECT count(*) AS total
                        FROM notifications notification
                        JOIN app_users owner ON owner.id = notification.recipient_user_id
                        WHERE owner.public_id = :owner AND notification.event_type = :eventType
                        """)
                .bind("owner", owner)
                .bind("eventType", eventType)
                .map((row, metadata) -> row.get("total", Long.class))
                .one()
                .block();
    }

    private long countOutboxEvents(String eventType, String notificationEventType) {
        return databaseClient.sql("""
                        SELECT count(*) AS total
                        FROM outbox_events event
                        WHERE event.event_type = :eventType
                          AND event.payload ->> 'notificationType' = :notificationEventType
                        """)
                .bind("eventType", eventType)
                .bind("notificationEventType", notificationEventType)
                .map((row, metadata) -> row.get("total", Long.class))
                .one()
                .block();
    }

    private record OutboxSnapshot(String eventType, Integer schemaVersion, UUID aggregatePublicId,
                                  String payload, Long deliveryCount) {
    }
}
