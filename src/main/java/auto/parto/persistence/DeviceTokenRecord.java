package auto.parto.persistence;

import java.time.Instant;
import java.util.UUID;

public record DeviceTokenRecord(
        UUID publicId,
        UUID ownerUserPublicId,
        String providerToken,
        String platform,
        String status,
        Instant createdAt,
        Instant lastSeenAt,
        Instant invalidatedAt) {
}
