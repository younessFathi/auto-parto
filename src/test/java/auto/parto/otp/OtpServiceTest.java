package auto.parto.otp;

import auto.parto.dto.CustomerDto;
import auto.parto.dto.OtpDetailsDto;
import auto.parto.utils.Constants;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.lang.reflect.Field;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class OtpServiceTest {

    private OtpService otpService;
    private CustomerDto customerDto;

    @BeforeEach
    public void setup() {
        otpService = new OtpService();
        customerDto = CustomerDto.builder()
                                .jwtKey("")
                                .phoneNumber("+2125660099")
                                .otpDetailsDto(
                                        OtpDetailsDto.builder()
                                                .otp("1234")
                                                .timestamp(LocalDateTime.now())
                                                .build()
                                )
                                .build();
    }

    @Test
    public void generateNotNullOtpTest() {
        StepVerifier.create(otpService.generate())
                .assertNext(Assertions::assertNotNull)
                .verifyComplete();
    }

    @Test
    public void generateCheckGeneratedDateTypeTest() {
        StepVerifier.create(otpService.generate())
                .assertNext(value-> assertThat(value).isInstanceOf(String.class))
                .verifyComplete();
    }

    @Test
    public void validateNominalTest() {
        StepVerifier.create(otpService.validate( customerDto , "1234"))
                .assertNext(Assertions::assertNotNull)
                .verifyComplete();

    }

    @Test
    public void validateExpiredTest() throws IllegalAccessException, NoSuchFieldException {
        injectOtpExpirationMinutesValueOnClass();
        StepVerifier.create(otpService.validate( customerDto , "1234"))
                .expectErrorMessage(Constants.Exception.EXPIRED_OTP)
                .verify();

    }

    @Test
    public void validateOtpExceptionTest() throws IllegalAccessException, NoSuchFieldException {
        //injectOtpExpirationMinutesValueOnClass();
        StepVerifier.create(otpService.validate( customerDto , "1235"))
                .expectErrorMessage(Constants.Exception.INVALID_OTP)
                .verify();

    }

    @Test
    public void validateOtpNullTest() throws IllegalAccessException, NoSuchFieldException {
        //injectOtpExpirationMinutesValueOnClass();
        customerDto.setOtpDetailsDto(null);
        StepVerifier.create(otpService.validate( customerDto , "1235"))
                .expectErrorMessage(Constants.Exception.INVALID_OTP)
                .verify();

    }

    private void injectOtpExpirationMinutesValueOnClass() throws NoSuchFieldException, IllegalAccessException {
        Field field = OtpService.class.getDeclaredField("otpExpirationMinutes");
        field.setAccessible(true);
        field.set(otpService, -1);
    }
}
