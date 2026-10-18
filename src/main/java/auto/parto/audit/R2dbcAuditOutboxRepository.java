package auto.parto.audit;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
@ConditionalOnProperty(prefix = "auto.parto.persistence.postgresql", name = "enabled", havingValue = "true")
public class R2dbcAuditOutboxRepository implements AuditOutboxRepository {

    private static final String EVENT_COLUMNS = """
            od.public_id AS delivery_id, od.lease_owner AS claim_owner, od.attempt_count,
            oe.event_id, oe.event_type, oe.schema_version, oe.aggregate_type,
            oe.aggregate_public_id, oe.payload, oe.correlation_id, oe.created_at
            """;

    private final DatabaseClient databaseClient;

    public R2dbcAuditOutboxRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Flux<AuditOutboxRecord> claimDue(String workerId, int limit, Instant now, Duration leaseDuration) {
        if (workerId == null || workerId.isBlank() || leaseDuration.isNegative()
                || leaseDuration.isZero()) {
            return Flux.error(new IllegalArgumentException("workerId, limit, and leaseDuration are required"));
        }
        try {
            AuditPersistenceLimits.requireBatchSize(limit);
        } catch (IllegalArgumentException exception) {
            return Flux.error(exception);
        }
        Instant leaseExpiresAt = now.plus(leaseDuration);
        String claimOwner = workerId + ":" + UUID.randomUUID();
        return databaseClient.sql("""
                        WITH candidates AS (
                            SELECT od.id
                            FROM outbox_deliveries od
                            WHERE od.destination = 'AUDIT'
                              AND od.available_at <= :now
                              AND (od.status = 'PENDING'
                                   OR (od.status = 'CLAIMED' AND od.lease_expires_at <= :now))
                            ORDER BY od.available_at, od.id
                            FOR UPDATE SKIP LOCKED
                            LIMIT :limit
                        )
                        UPDATE outbox_deliveries od
                        SET status = 'CLAIMED', lease_owner = :claimOwner,
                            lease_expires_at = :leaseExpiresAt,
                            attempt_count = od.attempt_count + 1
                        FROM candidates
                        WHERE od.id = candidates.id
                        RETURNING od.id
                        """)
                .bind("now", now)
                .bind("limit", limit)
                .bind("claimOwner", claimOwner)
                .bind("leaseExpiresAt", leaseExpiresAt)
                .map((row, metadata) -> row.get("id", Long.class))
                .all()
                .collectList()
                .flatMapMany(this::findByDeliveryIds);
    }

    @Override
    public Mono<AuditOutboxRecord> claimForReplay(
            UUID deliveryId, String workerId, Instant now, Duration leaseDuration) {
        if (deliveryId == null || workerId == null || workerId.isBlank()
                || leaseDuration.isNegative() || leaseDuration.isZero()) {
            return Mono.error(new IllegalArgumentException("deliveryId, workerId, and leaseDuration are required"));
        }
        String claimOwner = workerId + ":" + UUID.randomUUID();
        Instant leaseExpiresAt = now.plus(leaseDuration);
        return databaseClient.sql("""
                        UPDATE outbox_deliveries
                        SET status = 'CLAIMED', lease_owner = :claimOwner,
                            lease_expires_at = :leaseExpiresAt,
                            delivered_at = NULL, last_error = NULL,
                            attempt_count = attempt_count + 1
                        WHERE public_id = :deliveryId AND destination = 'AUDIT'
                          AND status <> 'CLAIMED'
                        RETURNING id
                        """)
                .bind("deliveryId", deliveryId)
                .bind("claimOwner", claimOwner)
                .bind("leaseExpiresAt", leaseExpiresAt)
                .map((row, metadata) -> row.get("id", Long.class))
                .one()
                .flatMapMany(id -> findByDeliveryIds(List.of(id)))
                .singleOrEmpty();
    }

    @Override
    public Mono<AuditOutboxRecord> findByEventId(UUID eventId) {
        return databaseClient.sql("SELECT " + EVENT_COLUMNS + " FROM outbox_deliveries od "
                        + "JOIN outbox_events oe ON oe.id = od.outbox_event_id "
                        + "WHERE od.destination = 'AUDIT' AND oe.event_id = :eventId")
                .bind("eventId", eventId)
                .map(this::mapRecord)
                .one();
    }

    @Override
    public Flux<AuditOutboxRecord> findDispatchedSince(Instant since, int limit) {
        return databaseClient.sql("SELECT " + EVENT_COLUMNS + " FROM outbox_deliveries od "
                        + "JOIN outbox_events oe ON oe.id = od.outbox_event_id "
                        + "WHERE od.destination = 'AUDIT' AND od.status = 'DISPATCHED' "
                        + "AND od.updated_at >= :since ORDER BY od.updated_at, od.id LIMIT :limit")
                .bind("since", since)
                .bind("limit", AuditPersistenceLimits.requireBatchSize(limit))
                .map(this::mapRecord)
                .all();
    }

    @Override
    public Mono<Void> markDelivered(UUID deliveryId, String claimOwner, Instant deliveredAt) {
        return databaseClient.sql("""
                        UPDATE outbox_deliveries
                        SET status = 'DISPATCHED', lease_owner = NULL, lease_expires_at = NULL,
                            delivered_at = :deliveredAt, last_error = NULL
                        WHERE public_id = :deliveryId AND destination = 'AUDIT'
                          AND status = 'CLAIMED' AND lease_owner = :claimOwner
                          AND lease_expires_at > CURRENT_TIMESTAMP
                        """)
                .bind("deliveryId", deliveryId)
                .bind("claimOwner", claimOwner)
                .bind("deliveredAt", deliveredAt)
                .fetch().rowsUpdated().flatMap(R2dbcAuditOutboxRepository::requireOneUpdatedRow);
    }

    @Override
    public Mono<Void> scheduleRetry(UUID deliveryId, String claimOwner, Instant availableAt, String failureCode) {
        return databaseClient.sql("""
                        UPDATE outbox_deliveries
                        SET status = 'PENDING', available_at = :availableAt,
                            lease_owner = NULL, lease_expires_at = NULL, last_error = :failureCode
                        WHERE public_id = :deliveryId AND destination = 'AUDIT'
                          AND status = 'CLAIMED' AND lease_owner = :claimOwner
                          AND lease_expires_at > CURRENT_TIMESTAMP
                        """)
                .bind("deliveryId", deliveryId)
                .bind("claimOwner", claimOwner)
                .bind("availableAt", availableAt)
                .bind("failureCode", failureCode)
                .fetch().rowsUpdated().flatMap(R2dbcAuditOutboxRepository::requireOneUpdatedRow);
    }

    @Override
    public Mono<Void> markExhausted(UUID deliveryId, String claimOwner, String failureCode) {
        return databaseClient.sql("""
                        UPDATE outbox_deliveries
                        SET status = 'EXHAUSTED', lease_owner = NULL, lease_expires_at = NULL,
                            last_error = :failureCode
                        WHERE public_id = :deliveryId AND destination = 'AUDIT'
                          AND status = 'CLAIMED' AND lease_owner = :claimOwner
                          AND lease_expires_at > CURRENT_TIMESTAMP
                        """)
                .bind("deliveryId", deliveryId)
                .bind("claimOwner", claimOwner)
                .bind("failureCode", failureCode)
                .fetch().rowsUpdated().flatMap(R2dbcAuditOutboxRepository::requireOneUpdatedRow);
    }

    @Override
    public Mono<Void> markForReplay(UUID deliveryId, Instant availableAt) {
        return databaseClient.sql("""
                        UPDATE outbox_deliveries
                        SET status = 'PENDING', available_at = :availableAt,
                            lease_owner = NULL, lease_expires_at = NULL, delivered_at = NULL,
                            last_error = NULL
                        WHERE public_id = :deliveryId AND destination = 'AUDIT'
                        """)
                .bind("deliveryId", deliveryId)
                .bind("availableAt", availableAt)
                .fetch().rowsUpdated().flatMap(R2dbcAuditOutboxRepository::requireOneUpdatedRow);
    }

    @Override
    public Mono<Integer> purgeTerminalBefore(Instant cutoff, int limit) {
        if (cutoff == null) {
            return Mono.error(new IllegalArgumentException("cutoff is required"));
        }
        final int boundedLimit;
        try {
            boundedLimit = AuditPersistenceLimits.requireBatchSize(limit);
        } catch (IllegalArgumentException exception) {
            return Mono.error(exception);
        }
        return databaseClient.sql("""
                        WITH candidates AS (
                            SELECT id
                            FROM outbox_deliveries
                            WHERE destination = 'AUDIT'
                              AND status IN ('DISPATCHED', 'EXHAUSTED')
                              AND updated_at < :cutoff
                            ORDER BY updated_at, id
                            LIMIT :limit
                        )
                        DELETE FROM outbox_deliveries od
                        USING candidates
                        WHERE od.id = candidates.id
                        """)
                .bind("cutoff", cutoff)
                .bind("limit", boundedLimit)
                .fetch().rowsUpdated()
                .map(Long::intValue);
    }

    private Flux<AuditOutboxRecord> findByDeliveryIds(List<Long> deliveryIds) {
        if (deliveryIds.isEmpty()) {
            return Flux.empty();
        }
        String placeholders = java.util.stream.IntStream.range(0, deliveryIds.size())
                .mapToObj(index -> ":id" + index)
                .collect(java.util.stream.Collectors.joining(", "));
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(
                "SELECT " + EVENT_COLUMNS + " FROM outbox_deliveries od "
                        + "JOIN outbox_events oe ON oe.id = od.outbox_event_id "
                        + "WHERE od.destination = 'AUDIT' AND od.id IN (" + placeholders + ") "
                        + "ORDER BY od.available_at, od.id");
        for (int index = 0; index < deliveryIds.size(); index++) {
            spec = spec.bind("id" + index, deliveryIds.get(index));
        }
        return spec.map(this::mapRecord).all();
    }

    private AuditOutboxRecord mapRecord(io.r2dbc.spi.Row row, io.r2dbc.spi.RowMetadata metadata) {
        return new AuditOutboxRecord(
                row.get("delivery_id", UUID.class),
                "AUDIT",
                row.get("claim_owner", String.class),
                new AuditEvent(
                        row.get("event_id", UUID.class),
                        row.get("event_type", String.class),
                        row.get("schema_version", Integer.class),
                        row.get("created_at", Instant.class),
                        null,
                        row.get("aggregate_type", String.class),
                        row.get("aggregate_public_id", UUID.class),
                        row.get("correlation_id", UUID.class),
                        "application-postgresql-outbox",
                        row.get("payload", String.class)),
                row.get("attempt_count", Integer.class));
    }

    private static Mono<Void> requireOneUpdatedRow(Long rowsUpdated) {
        return rowsUpdated == 1
                ? Mono.empty()
                : Mono.error(new IllegalStateException("audit delivery claim is no longer owned"));
    }
}
