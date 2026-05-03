package auto.parto.dto;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class OtpDetailsDtoTest {

    @Test
    void isLocked_returnsTrue_whenLockedUntilIsInFuture() {
        OtpDetailsDto otpDetails = OtpDetailsDto.builder()
                .lockedUntil(LocalDateTime.now().plusMinutes(10))
                .build();

        assertThat(otpDetails.isLocked()).isTrue();
    }

    @Test
    void isLocked_returnsFalse_whenLockedUntilIsInPast() {
        OtpDetailsDto otpDetails = OtpDetailsDto.builder()
                .lockedUntil(LocalDateTime.now().minusMinutes(10))
                .build();

        assertThat(otpDetails.isLocked()).isFalse();
    }

    @Test
    void isLocked_returnsFalse_whenLockedUntilIsNull() {
        OtpDetailsDto otpDetails = OtpDetailsDto.builder()
                .lockedUntil(null)
                .build();

        assertThat(otpDetails.isLocked()).isFalse();
    }

    @Test
    void isExpired_returnsTrue_whenOtpIsOlderThanExpirationMinutes() {
        OtpDetailsDto otpDetails = OtpDetailsDto.builder()
                .timestamp(LocalDateTime.now().minusMinutes(10))
                .build();

        assertThat(otpDetails.isExpired(5)).isTrue();
    }

    @Test
    void isExpired_returnsFalse_whenOtpIsWithinExpirationMinutes() {
        OtpDetailsDto otpDetails = OtpDetailsDto.builder()
                .timestamp(LocalDateTime.now().minusMinutes(3))
                .build();

        assertThat(otpDetails.isExpired(5)).isFalse();
    }

    @Test
    void constructor_withFourParams_setsAllFields() {
        LocalDateTime now = LocalDateTime.now();
        OtpDetailsDto otpDetails = new OtpDetailsDto("123456", now, 3, now.plusMinutes(15));

        assertThat(otpDetails.getOtp()).isEqualTo("123456");
        assertThat(otpDetails.getTimestamp()).isEqualTo(now);
        assertThat(otpDetails.getFailedAttempts()).isEqualTo(3);
        assertThat(otpDetails.getLockedUntil()).isEqualTo(now.plusMinutes(15));
    }
}