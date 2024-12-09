package auto.parto.otp;

import auto.parto.dto.OtpRequestDto;
import auto.parto.exception.TwilioException;
import auto.parto.utils.Constants;
import com.twilio.exception.ApiException;
import com.twilio.exception.AuthenticationException;
import com.twilio.exception.InvalidRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class WhatsAppOtpSenderTest {
    @Mock
    private OtpService otpService;
    @Mock
    private TwilioSender twilioSender;

    @InjectMocks
    private WhatsAppOtpSender whatsAppOtpSender;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testSendSuccessfulFlowTest() {
        // Arrange
        String phoneNumber = "123456789";
        String otp = "1234";
        String whatsappNumber = "whatsapp:+123456789";
        OtpRequestDto otpRequestDto = new OtpRequestDto();
        otpRequestDto.setPhoneNumber(phoneNumber);

        // Mocking
        when(otpService.generate()).thenReturn(Mono.just(otp));
        when(twilioSender.send(anyString(), anyString())).thenReturn(Mono.just("12345"));

        // Act
        Mono<String> result = whatsAppOtpSender.send(otpRequestDto);

        // Assert
        StepVerifier.create(result)
                .expectNext(otp)
                .verifyComplete();

        verify(otpService, times(1)).generate();
        verify(twilioSender, times(1)).send(anyString(), anyString());
    }

    @Test
    public void testSendApiErrorTest() {
        // Arrange
        String phoneNumber = "987654321";
        String otp = "5678";
        String whatsappNumber = "whatsapp:+987654321";
        OtpRequestDto otpRequestDto = new OtpRequestDto();
        otpRequestDto.setPhoneNumber(phoneNumber);

        // Mocking
        when(otpService.generate()).thenReturn(Mono.just(otp));
        when(twilioSender.send(anyString(), anyString()))
                .thenReturn(Mono.error(new ApiException("Twilio API error")));

        // Act
        Mono<String> result = whatsAppOtpSender.send(otpRequestDto);

        // Assert
        StepVerifier.create(result)
                .expectErrorMatches(throwable ->
                        throwable instanceof TwilioException &&
                                throwable.getMessage().equals("Twilio API error")
                )
                .verify();

        verify(otpService, times(1)).generate();
        verify(twilioSender, times(1)).send(anyString(), anyString());
    }

    @Test
    public void testSendInvalidRequestExceptionTest() {
        // Arrange
        String phoneNumber = "987654321";
        String otp = "5678";
        String whatsappNumber = "whatsapp:+987654321";
        OtpRequestDto otpRequestDto = new OtpRequestDto();
        otpRequestDto.setPhoneNumber(phoneNumber);

        // Mocking
        when(otpService.generate()).thenReturn(Mono.just(otp));
        when(twilioSender.send(anyString(), anyString()))
                .thenReturn(Mono.error(new InvalidRequestException("Twilio Request error")));

        // Act
        Mono<String> result = whatsAppOtpSender.send(otpRequestDto);

        // Assert
        StepVerifier.create(result)
                .expectErrorMatches(throwable ->
                        throwable instanceof TwilioException &&
                                throwable.getMessage().equals("Twilio Request error")
                )
                .verify();

        verify(otpService, times(1)).generate();
        verify(twilioSender, times(1)).send(anyString(), anyString());
    }

    @Test
    public void testSendAuthenticationExceptionTest() {
        // Arrange
        String phoneNumber = "987654321";
        String otp = "5678";
        String whatsappNumber = "whatsapp:+987654321";
        OtpRequestDto otpRequestDto = new OtpRequestDto();
        otpRequestDto.setPhoneNumber(phoneNumber);

        // Mocking
        when(otpService.generate()).thenReturn(Mono.just(otp));
        when(twilioSender.send(anyString(), anyString()))
                .thenReturn(Mono.error(new AuthenticationException("Twilio Authentication error")));

        // Act
        Mono<String> result = whatsAppOtpSender.send(otpRequestDto);

        // Assert
        StepVerifier.create(result)
                .expectErrorMatches(throwable ->
                        throwable instanceof TwilioException &&
                                throwable.getMessage().equals("Twilio Authentication error")
                )
                .verify();

        verify(otpService, times(1)).generate();
        verify(twilioSender, times(1)).send(anyString(), anyString());
    }

    @Test
    public void testSendUnexpectedErrorTest() {
        // Arrange
        String phoneNumber = "987654321";
        String otp = "5678";
        String whatsappNumber = "whatsapp:+987654321";
        OtpRequestDto otpRequestDto = new OtpRequestDto();
        otpRequestDto.setPhoneNumber(phoneNumber);

        // Mocking
        when(otpService.generate()).thenReturn(Mono.just(otp));
        when(twilioSender.send(anyString(), anyString()))
                .thenReturn(Mono.error((new RuntimeException("unexpected error"))));

        // Act
        Mono<String> result = whatsAppOtpSender.send(otpRequestDto);

        // Assert
        StepVerifier.create(result)
                .expectErrorMatches(throwable ->
                        throwable instanceof TwilioException
                )
                .verify();

        verify(otpService, times(1)).generate();
        verify(twilioSender, times(1)).send(anyString(), anyString());
    }

}
