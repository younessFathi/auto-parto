package auto.parto.dto;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;

@Data
@Builder
public class OtpDetailsDto {
    @JsonIgnore
    private String otp;
    @JsonIgnore
    private String otpHash;
    @JsonIgnore
    private String purpose;
    @JsonIgnore
    private String identity;
    private LocalDateTime timestamp;
    @JsonIgnore
    private LocalDateTime expiresAt;
    @JsonIgnore
    private LocalDateTime consumedAt;
    private int failedAttempts;
    @JsonIgnore
    private LocalDateTime lockedUntil;

    public OtpDetailsDto() {}

    public OtpDetailsDto(String otp, LocalDateTime timestamp) {
        this(otp, timestamp, 0, null);
    }

    public OtpDetailsDto(String otp, LocalDateTime timestamp, int failedAttempts, LocalDateTime lockedUntil) {
        this.otp = otp;
        this.timestamp = timestamp;
        this.failedAttempts = failedAttempts;
        this.lockedUntil = lockedUntil;
    }

    public OtpDetailsDto(String otp, String otpHash, String purpose, String identity,
                         LocalDateTime timestamp, LocalDateTime expiresAt,
                         LocalDateTime consumedAt, int failedAttempts, LocalDateTime lockedUntil) {
        this.otp = otp;
        this.otpHash = otpHash;
        this.purpose = purpose;
        this.identity = identity;
        this.timestamp = timestamp;
        this.expiresAt = expiresAt;
        this.consumedAt = consumedAt;
        this.failedAttempts = failedAttempts;
        this.lockedUntil = lockedUntil;
    }

    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now());
    }

    public boolean isExpired(long otpExpirationMinutes) {
        LocalDateTime expiry = expiresAt != null ? expiresAt : timestamp.plusMinutes(otpExpirationMinutes);
        return !expiry.isAfter(LocalDateTime.now());
    }
}
