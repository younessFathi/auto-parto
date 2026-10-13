package auto.parto.persistence;

import java.time.Instant;
import java.util.UUID;

public record NotificationRecord(UUID publicId, UUID recipientUserPublicId, String eventType,
                                 String payloadJson, String status, Instant createdAt, Instant updatedAt) {
}
