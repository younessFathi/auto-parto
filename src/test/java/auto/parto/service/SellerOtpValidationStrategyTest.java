package auto.parto.service;

import auto.parto.dto.OtpDetailsDto;
import auto.parto.dto.SellerDto;
import auto.parto.dto.TokenDto;
import auto.parto.enums.CustomerType;
import auto.parto.exception.OtpInvalidException;
import auto.parto.exception.OtpLockedException;
import auto.parto.otp.OtpService;
import auto.parto.service.iservice.ISellerService;
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
class SellerOtpValidationStrategyTest {

    @Mock
    private ISellerService sellerService;
    @Mock
    private OtpService otpService;
    @Mock
    private JwtTokenGenerator jwtTokenGenerator;

    private SellerOtpValidationStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new SellerOtpValidationStrategy(sellerService, otpService, jwtTokenGenerator);
    }

    @Test
    void validate_returnsError_whenAccountIsLocked() {
        String email = "seller@test.com";
        SellerDto seller = SellerDto.builder()
                .id("seller-123")
                .email(email)
                .otpDetailsDto(OtpDetailsDto.builder()
                        .otp("123456")
                        .timestamp(LocalDateTime.now())
                        .lockedUntil(LocalDateTime.now().plusMinutes(10))
                        .build())
                .build();

        when(sellerService.findSellerByEmail(email)).thenReturn(Mono.just(seller));

        StepVerifier.create(strategy.validate("123456", email))
                .expectError(OtpLockedException.class)
                .verify();
    }

    @Test
    void validate_recordsFailedAttempt_whenOtpIsInvalid() {
        String email = "seller@test.com";
        String otp = "wrong";
        SellerDto seller = SellerDto.builder()
                .id("seller-123")
                .email(email)
                .otpDetailsDto(OtpDetailsDto.builder()
                        .otp("123456")
                        .timestamp(LocalDateTime.now())
                        .failedAttempts(2)
                        .build())
                .build();

        when(sellerService.findSellerByEmail(email)).thenReturn(Mono.just(seller));
        when(otpService.validate(any(), eq(otp))).thenReturn(Mono.error(new OtpInvalidException("Invalid OTP")));
        when(sellerService.recordFailedOtpAttempt(email)).thenReturn(Mono.just(seller));

        StepVerifier.create(strategy.validate(otp, email))
                .expectError(OtpInvalidException.class)
                .verify();
    }
}