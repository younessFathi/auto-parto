package auto.parto.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.UUID;

@Document(collection = "otpDetails")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OtpDetails {
    private UUID id;
    /** Retained only for reading pre-AP-0006 records; new writes use otpHash. */
    private String otp;
    private String otpHash;
    private String purpose;
    private String identity;
    private LocalDateTime timestamp;
    private LocalDateTime expiresAt;
    private LocalDateTime consumedAt;
    private int failedAttempts;
    private LocalDateTime lockedUntil;

    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now());
    }

    public boolean isExpired(long otpExpirationMinutes) {
        LocalDateTime expiry = expiresAt != null ? expiresAt : timestamp.plusMinutes(otpExpirationMinutes);
        return !expiry.isAfter(LocalDateTime.now());
    }
}
