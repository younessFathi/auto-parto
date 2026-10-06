package auto.parto.persistence;

import java.time.Instant;
import java.util.UUID;

public record SellerIdentityRecord(
        UUID publicId,
        String email,
        String firstName,
        String lastName,
        boolean active,
        String otpHash,
        Instant otpExpiresAt,
        int failedAttempts,
        Instant lockedUntil) {
}
