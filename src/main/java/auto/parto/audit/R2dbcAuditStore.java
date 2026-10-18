package auto.parto.audit;

import io.r2dbc.postgresql.codec.Json;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Mono;

import java.util.Objects;

public final class R2dbcAuditStore implements AuditStore {

    private final DatabaseClient databaseClient;

    public R2dbcAuditStore(DatabaseClient databaseClient) {
        this.databaseClient = Objects.requireNonNull(databaseClient, "databaseClient");
    }

    @Override
    public Mono<AuditAppendResult> append(AuditEvent event) {
        Objects.requireNonNull(event, "event");
        DatabaseClient.GenericExecuteSpec statement = databaseClient.sql("""
                        INSERT INTO audit_events
                            (event_id, event_type, schema_version, occurred_at, actor_public_id,
                             subject_type, subject_public_id, correlation_id, source, payload)
                        VALUES (:eventId, :eventType, :schemaVersion, :occurredAt, :actorPublicId,
                                :subjectType, :subjectPublicId, :correlationId, :source, :payload)
                        ON CONFLICT (event_id) DO NOTHING
                        """)
                .bind("eventId", event.eventId())
                .bind("eventType", event.eventType())
                .bind("schemaVersion", event.schemaVersion())
                .bind("occurredAt", event.occurredAt())
                .bind("subjectType", event.subjectType())
                .bind("source", event.source())
                .bind("payload", Json.of(event.payloadJson()));
        statement = bindNullable(statement, "actorPublicId", event.actorPublicId(), java.util.UUID.class);
        statement = bindNullable(statement, "subjectPublicId", event.subjectPublicId(), java.util.UUID.class);
        statement = bindNullable(statement, "correlationId", event.correlationId(), java.util.UUID.class);
        return statement.fetch().rowsUpdated()
                .map(rows -> rows == 1 ? AuditAppendResult.CREATED : AuditAppendResult.DUPLICATE);
    }

    @Override
    public Mono<Boolean> contains(java.util.UUID eventId) {
        return databaseClient.sql("SELECT 1 FROM audit_events WHERE event_id = :eventId")
                .bind("eventId", eventId)
                .map((row, metadata) -> true)
                .one()
                .defaultIfEmpty(false);
    }

    private static DatabaseClient.GenericExecuteSpec bindNullable(
            DatabaseClient.GenericExecuteSpec statement, String name, Object value, Class<?> type) {
        return value == null ? statement.bindNull(name, type) : statement.bind(name, value);
    }
}
