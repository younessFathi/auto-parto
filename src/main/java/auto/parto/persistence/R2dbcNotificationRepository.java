package auto.parto.persistence;

import io.r2dbc.spi.Row;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;

@Repository
@ConditionalOnProperty(prefix = "auto.parto.persistence.postgresql.notifications", name = "enabled",
        havingValue = "true")
public class R2dbcNotificationRepository implements NotificationRepository {

    private static final String NOTIFICATION_REQUESTED_EVENT = "notification.requested";
    private static final int NOTIFICATION_EVENT_SCHEMA_VERSION = 1;
    private static final Set<String> STATUSES = Set.of("PENDING", "DISPATCHED", "EXHAUSTED");
    private static final Set<String> CHANNELS = Set.of("SSE", "FCM", "EMAIL");
    private static final String SELECT = """
            SELECT notification.public_id, owner.public_id AS recipient_public_id,
                   notification.event_type, notification.payload::text AS payload_json,
                   notification.status, notification.created_at, notification.updated_at
            FROM notifications notification
            JOIN app_users owner ON owner.id = notification.recipient_user_id
            """;

    private final DatabaseClient databaseClient;
    private final TransactionalOperator transactions;

    public R2dbcNotificationRepository(DatabaseClient databaseClient, TransactionalOperator transactions) {
        this.databaseClient = databaseClient;
        this.transactions = transactions;
    }

    @Override
    public Mono<NotificationRecord> enqueue(UUID recipientUserPublicId, String eventType, String payloadJson) {
        requirePublicId(recipientUserPublicId);
        requireText(eventType, "eventType");
        requireText(payloadJson, "payloadJson");
        NotificationPayloadLimits.requireSize(payloadJson);
        return databaseClient.sql("""
                        WITH inserted_notification AS (
                            INSERT INTO notifications (public_id, recipient_user_id, event_type, payload)
                            SELECT gen_random_uuid(), id, :eventType, CAST(:payloadJson AS jsonb)
                            FROM app_users
                            WHERE public_id = :recipientPublicId AND status = 'ACTIVE'
                            RETURNING id, public_id, event_type, payload
                        ), inserted_event AS (
                            INSERT INTO outbox_events
                                (event_id, event_type, schema_version, aggregate_type, aggregate_id,
                                 aggregate_public_id, payload)
                            SELECT gen_random_uuid(), :outboxEventType, :schemaVersion, 'notification',
                                   notification.id, notification.public_id,
                                   jsonb_build_object(
                                       'notificationId', notification.public_id,
                                       'recipientUserPublicId', :recipientPublicId,
                                       'notificationType', notification.event_type,
                                       'notificationPayload', notification.payload
                                   )
                            FROM inserted_notification notification
                            RETURNING id
                        )
                        SELECT notification.public_id
                        FROM inserted_notification notification
                        CROSS JOIN inserted_event
                        """)
                .bind("eventType", eventType)
                .bind("payloadJson", payloadJson)
                .bind("recipientPublicId", recipientUserPublicId)
                .bind("outboxEventType", NOTIFICATION_REQUESTED_EVENT)
                .bind("schemaVersion", NOTIFICATION_EVENT_SCHEMA_VERSION)
                .map((row, metadata) -> row.get("public_id", UUID.class))
                .one()
                .flatMap(this::findByPublicId)
                .as(transactions::transactional);
    }

    @Override
    public Flux<NotificationRecord> findPending(int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit must be between 1 and 100");
        return databaseClient.sql(SELECT + " WHERE notification.status = 'PENDING' ORDER BY notification.created_at LIMIT :limit")
                .bind("limit", limit)
                .map((row, metadata) -> map(row))
                .all();
    }

    @Override
    public Mono<Long> countPending() {
        return databaseClient.sql("SELECT count(*) AS total FROM notifications WHERE status = 'PENDING'")
                .map((row, metadata) -> row.get("total", Long.class)).one();
    }

    @Override
    public Flux<UUID> findPendingRecipientIds(int limit) {
        return findPendingRecipientIds(null, limit);
    }

    @Override
    public Flux<UUID> findPendingRecipientIds(UUID afterRecipientPublicId, int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit must be between 1 and 100");
        DatabaseClient.GenericExecuteSpec query = databaseClient.sql("""
                        SELECT owner.public_id
                        FROM notifications notification
                        JOIN app_users owner ON owner.id = notification.recipient_user_id
                        WHERE notification.status = 'PENDING'
                          AND (:afterRecipientPublicId IS NULL OR owner.public_id > :afterRecipientPublicId)
                        GROUP BY owner.public_id
                        ORDER BY owner.public_id
                        LIMIT :limit
                        """)
                .bind("limit", limit);
        query = afterRecipientPublicId == null
                ? query.bindNull("afterRecipientPublicId", UUID.class)
                : query.bind("afterRecipientPublicId", afterRecipientPublicId);
        return query
                .map((row, metadata) -> row.get("public_id", UUID.class))
                .all();
    }

    @Override
    public Flux<NotificationRecord> findPendingForRecipient(UUID recipientUserPublicId, int limit) {
        requirePublicId(recipientUserPublicId);
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit must be between 1 and 100");
        return databaseClient.sql(SELECT + """
                        WHERE notification.status = 'PENDING'
                          AND owner.public_id = :recipientPublicId
                        ORDER BY notification.created_at
                        LIMIT :limit
                        """)
                .bind("recipientPublicId", recipientUserPublicId)
                .bind("limit", limit)
                .map((row, metadata) -> map(row))
                .all();
    }

    @Override
    public Flux<NotificationRecord> findForRecipientAfter(UUID recipientUserPublicId,
                                                           UUID afterNotificationPublicId,
                                                           int limit) {
        requirePublicId(recipientUserPublicId);
        requirePublicId(afterNotificationPublicId);
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit must be between 1 and 100");
        return databaseClient.sql(SELECT + """
                        JOIN notifications cursor_notification
                          ON cursor_notification.public_id = :afterNotificationPublicId
                        WHERE owner.public_id = :recipientPublicId
                          AND (notification.created_at, notification.public_id)
                              > (cursor_notification.created_at, cursor_notification.public_id)
                        ORDER BY notification.created_at, notification.public_id
                        LIMIT :limit
                        """)
                .bind("recipientPublicId", recipientUserPublicId)
                .bind("afterNotificationPublicId", afterNotificationPublicId)
                .bind("limit", limit)
                .map((row, metadata) -> map(row))
                .all();
    }

    @Override
    public Mono<Boolean> cursorBelongsToRecipient(UUID recipientUserPublicId, UUID notificationPublicId) {
        requirePublicId(recipientUserPublicId);
        requirePublicId(notificationPublicId);
        return databaseClient.sql("""
                        SELECT EXISTS (
                            SELECT 1
                            FROM notifications notification
                            JOIN app_users owner ON owner.id = notification.recipient_user_id
                            WHERE notification.public_id = :notificationPublicId
                              AND owner.public_id = :recipientPublicId
                        ) AS cursor_exists
                        """)
                .bind("notificationPublicId", notificationPublicId)
                .bind("recipientPublicId", recipientUserPublicId)
                .map((row, metadata) -> Boolean.TRUE.equals(row.get("cursor_exists", Boolean.class)))
                .one()
                .defaultIfEmpty(false);
    }

    @Override
    public Mono<NotificationRecord> claimNotification(UUID notificationPublicId, String claimOwner,
                                                       Instant now, Duration leaseDuration) {
        requirePublicId(notificationPublicId);
        requireText(claimOwner, "claimOwner");
        if (leaseDuration == null || leaseDuration.isNegative() || leaseDuration.isZero()) {
            throw new IllegalArgumentException("leaseDuration must be positive");
        }
        return databaseClient.sql("""
                        UPDATE notifications
                        SET lease_owner = :claimOwner, lease_expires_at = :leaseExpiresAt,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE public_id = :notificationPublicId
                          AND status = 'PENDING'
                          AND next_attempt_at <= :now
                          AND (lease_expires_at IS NULL OR lease_expires_at <= :now)
                        RETURNING public_id
                        """)
                .bind("notificationPublicId", notificationPublicId)
                .bind("claimOwner", claimOwner)
                .bind("now", now)
                .bind("leaseExpiresAt", now.plus(leaseDuration))
                .map((row, metadata) -> row.get("public_id", UUID.class))
                .one()
                .flatMap(this::findByPublicId)
                .as(transactions::transactional);
    }

    @Override
    public Mono<Long> markPendingForRecipient(UUID recipientUserPublicId) {
        requirePublicId(recipientUserPublicId);
        return databaseClient.sql("""
                        UPDATE notifications notification
                        SET status = 'DISPATCHED'
                        FROM app_users owner
                        WHERE notification.recipient_user_id = owner.id
                          AND owner.public_id = :recipientPublicId
                          AND notification.status = 'PENDING'
                        """)
                .bind("recipientPublicId", recipientUserPublicId)
                .fetch().rowsUpdated()
                .as(transactions::transactional);
    }

    @Override
    public Mono<Boolean> markPendingForRecipientAndId(UUID recipientUserPublicId, String notificationId) {
        requirePublicId(recipientUserPublicId);
        requireText(notificationId, "notificationId");
        return databaseClient.sql("""
                        UPDATE notifications notification
                        SET status = 'DISPATCHED'
                        FROM app_users owner
                        WHERE notification.recipient_user_id = owner.id
                          AND owner.public_id = :recipientPublicId
                          AND notification.payload ->> 'id' = :notificationId
                          AND notification.status = 'PENDING'
                        """)
                .bind("recipientPublicId", recipientUserPublicId)
                .bind("notificationId", notificationId)
                .fetch().rowsUpdated()
                .map(rows -> rows == 1)
                .as(transactions::transactional);
    }

    @Override
    public Mono<Boolean> updateStatus(UUID notificationPublicId, String status) {
        requirePublicId(notificationPublicId);
        requireText(status, "status");
        if (!STATUSES.contains(status)) throw new IllegalArgumentException("unsupported notification status");
        return databaseClient.sql("""
                        UPDATE notifications SET status = :status
                        WHERE public_id = :publicId
                        """)
                .bind("status", status)
                .bind("publicId", notificationPublicId)
                .fetch().rowsUpdated()
                .map(rows -> rows == 1)
                .as(transactions::transactional);
    }

    @Override
    public Mono<NotificationAttemptRecord> recordAttempt(UUID notificationPublicId, String channel, String status,
                                                         String providerReference, String failureReason) {
        requirePublicId(notificationPublicId);
        requireText(channel, "channel");
        requireText(status, "status");
        String normalizedChannel = channel.toUpperCase(java.util.Locale.ROOT);
        if (!CHANNELS.contains(normalizedChannel)) throw new IllegalArgumentException("unsupported channel");
        var statement = databaseClient.sql("""
                        INSERT INTO notification_attempts
                            (public_id, notification_id, channel, status, provider_reference, failure_reason,
                             completed_at)
                        SELECT gen_random_uuid(), notification.id, :channel, :status, :providerReference,
                               :failureReason, CURRENT_TIMESTAMP
                        FROM notifications notification
                        WHERE notification.public_id = :notificationPublicId
                        RETURNING public_id, notification_id, channel, status, provider_reference, failure_reason,
                                  attempted_at, completed_at
                        """)
                .bind("channel", normalizedChannel).bind("status", status)
                .bind("notificationPublicId", notificationPublicId);
        statement = providerReference == null ? statement.bindNull("providerReference", String.class)
                : statement.bind("providerReference", providerReference);
        statement = failureReason == null ? statement.bindNull("failureReason", String.class)
                : statement.bind("failureReason", failureReason);
        return statement
                .map((row, metadata) -> new NotificationAttemptRecord(row.get("public_id", UUID.class),
                        notificationPublicId, row.get("channel", String.class), row.get("status", String.class),
                        row.get("provider_reference", String.class), row.get("failure_reason", String.class),
                        row.get("attempted_at", Instant.class), row.get("completed_at", Instant.class)))
                .one()
                .as(transactions::transactional);
    }

    @Override
    public Flux<NotificationRecord> claimPendingForRecipient(UUID recipientUserPublicId, String claimOwner,
                                                              Instant now, Duration leaseDuration, int limit) {
        requirePublicId(recipientUserPublicId);
        requireText(claimOwner, "claimOwner");
        requireLimit(limit);
        if (leaseDuration == null || leaseDuration.isNegative() || leaseDuration.isZero()) {
            throw new IllegalArgumentException("leaseDuration must be positive");
        }
        Instant leaseExpiresAt = now.plus(leaseDuration);
        return databaseClient.sql("""
                        WITH candidates AS (
                            SELECT notification.id
                            FROM notifications notification
                            JOIN app_users owner ON owner.id = notification.recipient_user_id
                            WHERE owner.public_id = :recipientPublicId
                              AND notification.status = 'PENDING'
                              AND notification.next_attempt_at <= :now
                              AND (notification.lease_expires_at IS NULL
                                   OR notification.lease_expires_at <= :now)
                            ORDER BY notification.created_at, notification.id
                            FOR UPDATE SKIP LOCKED
                            LIMIT :limit
                        )
                        UPDATE notifications notification
                        SET lease_owner = :claimOwner, lease_expires_at = :leaseExpiresAt,
                            updated_at = CURRENT_TIMESTAMP
                        FROM candidates
                        WHERE notification.id = candidates.id
                        RETURNING notification.public_id
                        """)
                .bind("recipientPublicId", recipientUserPublicId)
                .bind("claimOwner", claimOwner)
                .bind("now", now)
                .bind("leaseExpiresAt", leaseExpiresAt)
                .bind("limit", limit)
                .map((row, metadata) -> row.get("public_id", UUID.class))
                .all()
                .flatMap(this::findByPublicId)
                .as(transactions::transactional);
    }

    @Override
    public Mono<Boolean> acknowledgeDelivery(UUID notificationPublicId, String claimOwner) {
        requirePublicId(notificationPublicId);
        requireText(claimOwner, "claimOwner");
        return databaseClient.sql("""
                        UPDATE notifications
                        SET status = 'DISPATCHED', lease_owner = NULL, lease_expires_at = NULL,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE public_id = :publicId AND status = 'PENDING'
                          AND lease_owner = :claimOwner AND lease_expires_at > CURRENT_TIMESTAMP
                        """)
                .bind("publicId", notificationPublicId)
                .bind("claimOwner", claimOwner)
                .fetch().rowsUpdated()
                .map(rows -> rows == 1)
                .as(transactions::transactional);
    }

    @Override
    public Mono<Boolean> scheduleRetry(UUID notificationPublicId, String claimOwner, Instant nextAttemptAt,
                                       String failureCode, int maxAttempts) {
        requirePublicId(notificationPublicId);
        requireText(claimOwner, "claimOwner");
        requireText(failureCode, "failureCode");
        if (maxAttempts < 1 || maxAttempts > 10) throw new IllegalArgumentException("maxAttempts must be between 1 and 10");
        return databaseClient.sql("""
                        UPDATE notifications
                        SET attempt_count = attempt_count + 1,
                            status = CASE WHEN attempt_count + 1 >= :maxAttempts THEN 'EXHAUSTED' ELSE 'PENDING' END,
                            next_attempt_at = :nextAttemptAt,
                            last_failure_code = :failureCode,
                            lease_owner = NULL, lease_expires_at = NULL,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE public_id = :publicId AND status = 'PENDING'
                          AND lease_owner = :claimOwner AND lease_expires_at > CURRENT_TIMESTAMP
                        """)
                .bind("maxAttempts", maxAttempts)
                .bind("nextAttemptAt", nextAttemptAt)
                .bind("failureCode", failureCode)
                .bind("publicId", notificationPublicId)
                .bind("claimOwner", claimOwner)
                .fetch().rowsUpdated()
                .map(rows -> rows == 1)
                .as(transactions::transactional);
    }

    private Mono<NotificationRecord> findByPublicId(UUID publicId) {
        return databaseClient.sql(SELECT + " WHERE notification.public_id = :publicId")
                .bind("publicId", publicId)
                .map((row, metadata) -> map(row))
                .one();
    }

    private static NotificationRecord map(Row row) {
        return new NotificationRecord(row.get("public_id", UUID.class), row.get("recipient_public_id", UUID.class),
                row.get("event_type", String.class), row.get("payload_json", String.class),
                row.get("status", String.class), row.get("created_at", Instant.class),
                row.get("updated_at", Instant.class));
    }

    private static void requirePublicId(UUID value) {
        if (value == null) throw new IllegalArgumentException("public id is required");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }

    private static void requireLimit(int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit must be between 1 and 100");
    }
}
