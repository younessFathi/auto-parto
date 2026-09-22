package auto.parto.api;

import auto.parto.dto.*;
import auto.parto.enums.CustomerType;
import auto.parto.otp.EmailService;
import auto.parto.otp.OtpService;
import auto.parto.service.iservice.AuthStrategy;
import auto.parto.service.iservice.IBuyerService;
import auto.parto.service.iservice.OtpValidationStrategy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = AuthController.class, properties = {
    "auth.provider=otp"
})
@Import({TestConfig.class, AuthControllerTest.AuthMockConfig.class})
class AuthControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private AuthStrategy authStrategy;

    @Autowired
    private OtpValidationStrategy<JwtKeyProvider> otpValidationStrategy;

    @MockitoBean
    private EmailService emailService;

    @MockitoBean
    private OtpService otpService;

    @TestConfiguration
    static class AuthMockConfig {
        @Bean
        AuthStrategy authStrategy() {
            AuthStrategy mock = mock(AuthStrategy.class);
            when(mock.getSupportedType()).thenReturn(CustomerType.BUYER);
            return mock;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        @Bean
        OtpValidationStrategy<JwtKeyProvider> otpValidationStrategy() {
            OtpValidationStrategy<JwtKeyProvider> mock = mock(OtpValidationStrategy.class);
            when(mock.getSupportedType()).thenReturn(CustomerType.BUYER);
            return mock;
        }
    }

    @Test
    void sendOtpEmail_returns200() {
        OtpRequestDto request = OtpRequestDto.builder()
                .email("test@test.com")
                .customerType(CustomerType.BUYER)
                .build();

        when(otpService.generate()).thenReturn("123456");
        when(emailService.sendOTPEmail("test@test.com", "123456")).thenReturn(Mono.just("123456"));
        when(authStrategy.create(any(OtpRequestDto.class), anyString())).thenReturn(Mono.just("success"));

        webTestClient.post()
                .uri("/auth/send-otp-email")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void sendOtpEmail_returns400_whenEmailMissing() {
        OtpRequestDto request = OtpRequestDto.builder()
                .customerType(CustomerType.BUYER)
                .build();

        webTestClient.post()
                .uri("/auth/send-otp-email")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().is5xxServerError();
    }

    @Test
    void validateOtp_returnsToken() {
        ValidateOtpRequest request = new ValidateOtpRequest();
        request.setOtp("123456");
        request.setEmail("test@test.com");
        request.setCustomerType(CustomerType.BUYER);

        TokenDto<JwtKeyProvider> tokenDto = TokenDto.<JwtKeyProvider>builder().token("jwt-token").build();
        when(otpValidationStrategy.validate(anyString(), anyString())).thenReturn(Mono.just(tokenDto));

        webTestClient.post()
                .uri("/auth/validate-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.token").isEqualTo("jwt-token");
    }

    @Test
    void validateOtp_returns400_whenCustomerTypeMissing() {
        ValidateOtpRequest request = new ValidateOtpRequest();
        request.setOtp("123456");
        request.setEmail("test@test.com");

        webTestClient.post()
                .uri("/auth/validate-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest();
    }

}
