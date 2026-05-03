package auto.parto.dto;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.LocalDateTime;

@Data
@Builder
@Slf4j
public class OtpDetailsDto {
    private String otp;
    private LocalDateTime timestamp;
    private int failedAttempts;
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

    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now());
    }

    public boolean isExpired(long otpExpirationMinutes) {
        log.info("duration {} " , Duration.between(timestamp, LocalDateTime.now()).toMinutes());
        return Duration.between(timestamp, LocalDateTime.now()).toMinutes() > otpExpirationMinutes;
    }
}
