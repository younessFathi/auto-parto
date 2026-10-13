package auto.parto.persistence;

import java.time.Instant;
import java.util.UUID;

public record FileRecord(UUID publicId, UUID ownerUserPublicId, UUID partRequestPublicId,
                         UUID partListingPublicId, String objectKey, String originalFilename,
                         String contentType, long byteSize, String checksum, String status,
                         Instant createdAt, Instant updatedAt) {
}
