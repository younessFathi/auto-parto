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

import java.util.UUID;

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
