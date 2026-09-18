package auto.parto.audit;

import java.util.Objects;
import java.util.UUID;

public record AuditOutboxRecord(
        UUID deliveryId,
        String destination,
        String claimOwner,
        AuditEvent event,
        int attemptCount) {

    public AuditOutboxRecord {
        Objects.requireNonNull(deliveryId, "deliveryId");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(event, "event");
        if (attemptCount < 0) {
            throw new IllegalArgumentException("attemptCount must not be negative");
        }
    }
}
