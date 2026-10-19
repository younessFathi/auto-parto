package auto.parto.service;

import auto.parto.dto.BuyerDto;
import auto.parto.dto.OtpDetailsDto;
import auto.parto.dto.TokenDto;
import auto.parto.enums.CustomerType;
import auto.parto.exception.OtpInvalidException;
import auto.parto.service.iservice.IBuyerService;
import auto.parto.utils.JwtTokenGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BuyerOtpValidationStrategyTest {

    @Mock
    private IBuyerService buyerService;
    @Mock
    private JwtTokenGenerator jwtTokenGenerator;

    private BuyerOtpValidationStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new BuyerOtpValidationStrategy(buyerService, jwtTokenGenerator);
    }

    @Test
    void validate_returnsGenericError_whenAccountIsLocked() {
        String email = "buyer@test.com";
        BuyerDto buyer = BuyerDto.builder()
                .id("buyer-123")
                .email(email)
                .otpDetailsDto(OtpDetailsDto.builder()
                        .otp("123456")
                        .timestamp(LocalDateTime.now())
                        .lockedUntil(LocalDateTime.now().plusMinutes(10))
                        .build())
                .build();

        when(buyerService.findBuyerByEmail(email)).thenReturn(Mono.just(buyer));
        when(buyerService.consumeValidOtp(eq(email), eq("123456"), any(LocalDateTime.class))).thenReturn(Mono.just(false));
        when(buyerService.recordFailedOtpAttemptByEmail(email)).thenReturn(Mono.just(buyer));

        StepVerifier.create(strategy.validate("123456", email))
                .expectError(OtpInvalidException.class)
                .verify();
    }

    @Test
    void validate_recordsFailedAttempt_whenOtpIsInvalid() {
        String email = "buyer@test.com";
        String otp = "wrong";
        BuyerDto buyer = BuyerDto.builder()
                .id("buyer-123")
                .email(email)
                .otpDetailsDto(OtpDetailsDto.builder()
                        .otp("123456")
                        .timestamp(LocalDateTime.now())
                        .failedAttempts(2)
                        .build())
                .build();

        when(buyerService.findBuyerByEmail(email)).thenReturn(Mono.just(buyer));
        when(buyerService.consumeValidOtp(eq(email), eq(otp), any(LocalDateTime.class))).thenReturn(Mono.just(false));
        when(buyerService.recordFailedOtpAttemptByEmail(email)).thenReturn(Mono.just(buyer));

        StepVerifier.create(strategy.validate(otp, email))
                .expectError(OtpInvalidException.class)
                .verify();
    }
}
