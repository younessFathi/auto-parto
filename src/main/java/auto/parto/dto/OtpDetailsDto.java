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
    private final String otp;
    private final LocalDateTime timestamp;

    public OtpDetailsDto(String otp, LocalDateTime timestamp) {
        this.otp = otp;
        this.timestamp = timestamp;
    }

    public boolean isExpired(long otpExpirationMinutes) {
        log.info("duration {} " , Duration.between(timestamp, LocalDateTime.now()).toMinutes());
        return Duration.between(timestamp, LocalDateTime.now()).toMinutes() > otpExpirationMinutes;
    }
}
