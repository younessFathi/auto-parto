package auto.parto.audit;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record AuditEvent(
        UUID eventId,
        String eventType,
        int schemaVersion,
        Instant occurredAt,
        UUID actorPublicId,
        String subjectType,
        UUID subjectPublicId,
        UUID correlationId,
        String source,
        String payloadJson) {

    private static final int MAX_PAYLOAD_LENGTH = 128 * 1024;

    public AuditEvent {
        Objects.requireNonNull(eventId, "eventId");
        requireText(eventType, "eventType");
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("schemaVersion must be positive");
        }
        Objects.requireNonNull(occurredAt, "occurredAt");
        requireText(source, "source");
        Objects.requireNonNull(payloadJson, "payloadJson");
        if (payloadJson.length() > MAX_PAYLOAD_LENGTH) {
            throw new IllegalArgumentException("audit payload exceeds the bounded size");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
