package auto.parto.api;


import auto.parto.dto.OtpRequestDto;
import auto.parto.otp.WhatsAppOtpSender;
import auto.parto.otp.WhatsAppOtpValidator;
import auto.parto.utils.JwtTokenGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import static org.mockito.Mockito.*;
import org.springframework.security.test.context.support.WithMockUser;

@WebFluxTest(controllers = AuthController.class)
public class AuthControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private WhatsAppOtpSender whatsAppOtpSender;

    @MockitoBean
    private WhatsAppOtpValidator whatsAppOtpValidator;

    @MockitoBean
    private JwtTokenGenerator jwtTokenGenerator;

    @Test
    @WithMockUser
    void testSendOTP_Success() {
        String phoneNumber = "1234567890";
        OtpRequestDto otpRequestDto = OtpRequestDto.builder().phoneNumber(phoneNumber).build();

        doNothing().when(whatsAppOtpSender).send(otpRequestDto);

        webTestClient.mutateWith(SecurityMockServerConfigurers.csrf())
                .post()
                .uri("/auth/send-otp")
                .bodyValue(otpRequestDto)
                .exchange()
                .expectStatus().isOk() // Validate HTTP status
                .expectBody(String.class)
                .isEqualTo("OTP sent successfully!"); // Validate response body

        verify(whatsAppOtpSender, times(1)).send(otpRequestDto);
    }

    @Test
    @WithMockUser
    void testSendOTP_Failure() {
        String phoneNumber = "1234567890";
        OtpRequestDto otpRequestDto = OtpRequestDto.builder().phoneNumber(phoneNumber).build();
        doThrow(new RuntimeException("Failed to send OTP")).when(whatsAppOtpSender).send(otpRequestDto);

        webTestClient.mutateWith(SecurityMockServerConfigurers.csrf())
                .post()
                .uri("/auth/send-otp")
                .bodyValue(otpRequestDto)
                .exchange()
                .expectStatus().is5xxServerError();

        verify(whatsAppOtpSender, times(1)).send(otpRequestDto);
    }
}