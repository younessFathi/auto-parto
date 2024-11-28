package auto.parto.dto;

import lombok.Getter;

import java.time.LocalDateTime;

public class OtpDetailsDto {
    @Getter
    private final String otp;
    private final LocalDateTime timestamp;
    private final long otpExpirationMinutes;

    public OtpDetailsDto(String otp, LocalDateTime timestamp , long otpExpirationMinutes) {
        this.otp = otp;
        this.timestamp = timestamp;
        this.otpExpirationMinutes = otpExpirationMinutes;
    }

    public boolean isExpired() {
        return timestamp.plusMinutes(otpExpirationMinutes).isBefore(LocalDateTime.now());
    }
}
