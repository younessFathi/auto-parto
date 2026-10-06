package auto.parto.persistence;

import java.time.Instant;
import java.util.UUID;

public record BuyerIdentityRecord(
        UUID publicId,
        String email,
        String firstName,
        String lastName,
        String phoneNumber,
        boolean active,
        String otpHash,
        Instant otpExpiresAt,
        int failedAttempts,
        Instant lockedUntil) {
}
