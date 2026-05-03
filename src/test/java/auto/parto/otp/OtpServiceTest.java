package auto.parto.otp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    @InjectMocks
    private OtpService otpService;

    @Test
    void generate_returnsSixDigitOtp() {
        ReflectionTestUtils.setField(otpService, "otpExpirationMinutes", 5L);

        String otp = otpService.generate();

        assertThat(otp).hasSize(6);
        assertThat(otp).matches("\\d{6}");
    }

    @Test
    void generate_returnsOtpWithinValidRange() {
        ReflectionTestUtils.setField(otpService, "otpExpirationMinutes", 5L);

        for (int i = 0; i < 100; i++) {
            String otp = otpService.generate();
            int otpValue = Integer.parseInt(otp);
            assertThat(otpValue).isBetween(0, 999999);
        }
    }
}