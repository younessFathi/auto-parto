package auto.parto.persistence;

import java.time.Instant;
import java.util.UUID;

public record NotificationAttemptRecord(UUID publicId, UUID notificationPublicId, String channel,
                                       String status, String providerReference, String failureReason,
                                       Instant attemptedAt, Instant completedAt) {
}
