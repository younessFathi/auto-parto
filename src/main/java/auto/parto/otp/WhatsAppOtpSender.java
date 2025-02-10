package auto.parto.otp;

import auto.parto.dto.OtpRequestDto;
import auto.parto.exception.TwilioException;
import auto.parto.utils.Constants;
import com.twilio.exception.ApiException;
import com.twilio.exception.AuthenticationException;
import com.twilio.exception.InvalidRequestException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service("whatsapp")
@AllArgsConstructor
public class WhatsAppOtpSender implements OtpSender {
    private OtpService otpService;
    private TwilioOtpSender twilioSender;
    private Sender sender;

    @Override
    public Mono<String> send(OtpRequestDto otpRequestDto) {
        log.info("{} : {}", Constants.OTPConstants.SENDING_OTP_TO, otpRequestDto.getPhoneNumber());
        String whatsappNumber = Constants.OTPConstants.WHATSAPP + otpRequestDto.getPhoneNumber();
        return sender.send(whatsappNumber, twilioSender::send).onErrorMap(this::handleException);
    }

    // Centralized exception handling
    private TwilioException handleException(Throwable e) {
        switch (e) {
            case InvalidRequestException invalidRequestException -> {
                log.error("{} : {}", Constants.Exception.INVALID_REQUEST, e.getMessage());
                return new TwilioException(e.getMessage(), e);
            }
            case AuthenticationException authenticationException -> {
                log.error("{} : {}", Constants.Exception.AUTHENTICATION_FAILED, e.getMessage());
                return new TwilioException(e.getMessage(), e);
            }
            case ApiException apiException -> {
                log.error("{} : {}", Constants.Exception.API_ERROR, e.getMessage());
                return new TwilioException(e.getMessage(), e);
            }
            case null, default -> {
                assert e != null;
                log.error("{} : {}", Constants.Exception.UNEXPECTED_ERROR, e.getMessage());
                return new TwilioException(e.getMessage(), e);
            }
        }
    }

}
