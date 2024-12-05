package auto.parto.api;


import auto.parto.dto.CustomerDto;
import auto.parto.dto.OtpRequestDto;
import auto.parto.otp.OtpService;
import auto.parto.otp.WhatsAppOtpSender;
import auto.parto.otp.WhatsAppOtpValidator;
import auto.parto.service.CustomerService;
import auto.parto.utils.Constants;
import auto.parto.utils.JwtTokenGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import static org.mockito.Mockito.*;
import org.springframework.security.test.context.support.WithMockUser;
import reactor.core.publisher.Mono;

import java.util.Map;

@WebFluxTest(controllers = AuthController.class)
public class AuthControllerIT {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private WhatsAppOtpSender whatsAppOtpSender;

    @MockitoBean
    private WhatsAppOtpValidator whatsAppOtpValidator;

    @MockitoBean
    private JwtTokenGenerator jwtTokenGenerator;

    @MockitoBean
    private OtpService otpService;

    @MockitoBean
    private CustomerService customerService;

    private CustomerDto customerDto;
    @BeforeEach
    public void setup() {
       customerDto =  CustomerDto.builder()
                                .email("jane.smith@example.com")
                                .lastName("Smith")
                                .firstName("Jane")
                                .phoneNumber("1234567890")
                                .build();
    }

    @Test
    @WithMockUser
    void testSendOTP_Success() {
        String phoneNumber = "+1234567890";
        OtpRequestDto otpRequestDto = OtpRequestDto.builder().phoneNumber(phoneNumber).email("youness@gmail.com").build();

        when(whatsAppOtpSender.send(any())).thenReturn(Mono.just("1234"));
        when(customerService.checkIfCustomerExistUsingPhoneNumber(anyString())).thenReturn(Mono.just(false));
        when(customerService.create(any() , anyString())).thenReturn(Mono.just(customerDto));
        when(customerService.updateGeneratedOtp(any() , anyString())).thenReturn(Mono.empty());

        webTestClient.mutateWith(SecurityMockServerConfigurers.csrf())
                .post()
                .uri("/auth/send-otp")
                .bodyValue(otpRequestDto)
                .exchange()
                .expectStatus().isOk() // Validate HTTP status
                .expectBody(CustomerDto.class)
                .isEqualTo(customerDto); // Validate response body

        verify(whatsAppOtpSender, times(1)).send(otpRequestDto);
    }

    @Test
    @WithMockUser
    void testSendOTP_Failure() {
        String phoneNumber = "+1234567890000005555555";
        OtpRequestDto otpRequestDto = OtpRequestDto.builder().phoneNumber(phoneNumber).email("youness@gmail.com").build();
        when(whatsAppOtpSender.send(any())).thenReturn(Mono.just("1234"));
        when(customerService.checkIfCustomerExistUsingPhoneNumber(anyString())).thenReturn(Mono.just(false));
        when(customerService.create(any() , anyString())).thenReturn(Mono.just(customerDto));
        when(customerService.updateGeneratedOtp(any() , anyString())).thenReturn(Mono.empty());

        webTestClient.mutateWith(SecurityMockServerConfigurers.csrf())
                .post()
                .uri("/auth/send-otp")
                .bodyValue(otpRequestDto)
                .exchange()
                .expectStatus().is4xxClientError();
    }

    @Test
    @WithMockUser
    public void testValidateOTP_ValidCustomer_ReturnsToken() {
        // Arrange
        String otp = "1234";
        String phoneNumber = "1234567890";

        when(customerService.findCustomerByPhoneNumber(anyString())).thenReturn(Mono.just(customerDto));
        when(whatsAppOtpValidator.validate(customerDto, otp)).thenReturn(Mono.just(""));
        when(jwtTokenGenerator.generate(anyString() , anyString())).thenReturn(Mono.just("mocked-jwt-token"));

        // Act & Assert
        webTestClient.mutateWith(SecurityMockServerConfigurers.csrf())
                .get()
                .uri("/auth/validate-otp/{otp}/{phoneNumber}", otp, phoneNumber)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo("mocked-jwt-token");
    }

    @Test
    @WithMockUser
    public void testValidateOTP_InvalidOTP_ReturnsError() {
        // Arrange
        String otp = "12344";
        String phoneNumber = "9876543210";

        when(whatsAppOtpValidator.validate(customerDto, otp)).thenReturn(Mono.empty());

        // Act & Assert
        webTestClient.mutateWith(SecurityMockServerConfigurers.csrf())
                .get()
                .uri("/auth/validate-otp/{otp}/{phoneNumber}", otp, phoneNumber)
                .exchange()
                .expectStatus().is5xxServerError();
    }
}