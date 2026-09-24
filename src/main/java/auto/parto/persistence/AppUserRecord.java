package auto.parto.persistence;

import java.time.Instant;
import java.util.UUID;

/**
 * PostgreSQL representation of an application identity at the persistence boundary.
 * Internal database IDs and audit-reference columns are intentionally not exposed.
 */
public record AppUserRecord(
        UUID publicId,
        String provider,
        String providerSubject,
        String email,
        String role,
        String status,
        String keyVersion,
        Instant createdAt,
        Instant updatedAt) {
}
