package auto.parto.otp;

import auto.parto.dto.OtpRequestDto;
import auto.parto.exception.TwilioException;
import auto.parto.utils.Constants;
import com.twilio.Twilio;
import com.twilio.exception.ApiException;
import com.twilio.exception.AuthenticationException;
import com.twilio.exception.InvalidRequestException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@AllArgsConstructor
public class WhatsAppOtpSender implements OtpSender {
    private OtpService otpService;
    private TwilioSender twilioSender;

    @Override
    public Mono<String> send(OtpRequestDto otpRequestDto) {
        log.info("{} : {}", Constants.OTPConstants.SENDING_OTP_TO, otpRequestDto.getPhoneNumber());

        // Generate OTP and format the WhatsApp number
       return otpService.generate()
                .flatMap(
                        otp -> {
                            log.info("{} : {}", Constants.OTPConstants.GENERATED_OTP, otp);
                            String whatsappNumber = Constants.OTPConstants.WHATSAPP + otpRequestDto.getPhoneNumber();
                            return twilioSender.send(whatsappNumber, otp)
                                    .onErrorMap(this::handleException)
                                    .flatMap(
                                    (sid) -> {
                                        log.info("{} : {}" , Constants.OTPConstants.MESSAGE_SID, sid);
                                        return Mono.just(otp);
                                    }
                            )
                             .doOnTerminate(()->log.info("{} : {}", Constants.OTPConstants.END_OF_SEND_OTP_TO, otpRequestDto.getPhoneNumber()));
                        }
                );
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
