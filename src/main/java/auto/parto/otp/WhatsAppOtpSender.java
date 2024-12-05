package auto.parto.otp;

import auto.parto.dto.OtpRequestDto;
import auto.parto.exception.TwilioException;
import auto.parto.utils.Constants;
import com.twilio.Twilio;
import com.twilio.exception.ApiException;
import com.twilio.exception.AuthenticationException;
import com.twilio.exception.InvalidRequestException;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class WhatsAppOtpSender implements OtpSender {
    public static final String GENERATED_OTP = "Generated otp";
    public static final String MESSAGE_SID = "Message SID";
    private final OtpService otpService;

    public WhatsAppOtpSender(OtpService otpService ,
                             @Value("${twilio.sid}") String sid,
                             @Value("${twilio.auth}") String authToken) {
        this.otpService = otpService;
        Twilio.init(sid, authToken);
    }

    @Override
    public Mono<String> send(OtpRequestDto otpRequestDto) {
        log.info("{} {}", Constants.OTPConstants.SENDING_OTP_TO, otpRequestDto.getPhoneNumber());

        // Generate OTP and format the WhatsApp number
       return otpService.generate(otpRequestDto)
                .flatMap(
                        otp -> {
                            log.info("{} : {}", GENERATED_OTP, otp);
                            String whatsappNumber = Constants.OTPConstants.WHATSAPP + otpRequestDto.getPhoneNumber();
                            return sendWhatsAppMessage(whatsappNumber, otp)
                                    .onErrorMap(this::handleException)
                                    .flatMap(
                                    (message) -> {
                                        log.info("{} : {}" , MESSAGE_SID, message.getSid());
                                        return Mono.just(otp);
                                    }
                            )
                             .doOnTerminate(()->log.info("{} {}", Constants.OTPConstants.END_OF_SEND_OTP_TO, otpRequestDto.getPhoneNumber()));
                        }
                );
    }

    // Method to send WhatsApp message
    private Mono<Message> sendWhatsAppMessage(String whatsappNumber, String otp) {
        return Mono.defer(
                ()->{
                   try{
                      return Mono.just(Message.creator(
                               new PhoneNumber(whatsappNumber),
                               new PhoneNumber(Constants.OTPConstants.WHATSAPP_YOUR_TWILIO_WHATSAPP_NUMBER),
                               Constants.OTPConstants.YOUR_OTP_IS + otp
                       ).create());
                   }catch(Exception e) {
                        return Mono.error(e);
                   }
            });
    }

    // Centralized exception handling
    private TwilioException handleException(Throwable e) {
        switch (e) {
            case InvalidRequestException invalidRequestException -> {
                log.error(Constants.LOG_MESSAGE, Constants.ExceptionConstants.INVALID_REQUEST, e.getMessage());
                return new TwilioException(e.getMessage(), e);
            }
            case AuthenticationException authenticationException -> {
                log.error(Constants.LOG_MESSAGE, Constants.ExceptionConstants.AUTHENTICATION_FAILED, e.getMessage());
                return new TwilioException(e.getMessage(), e);
            }
            case ApiException apiException -> {
                log.error(Constants.LOG_MESSAGE, Constants.ExceptionConstants.API_ERROR, e.getMessage());
                return new TwilioException(e.getMessage(), e);
            }
            case null, default -> {
                assert e != null;
                log.error(Constants.LOG_MESSAGE, Constants.ExceptionConstants.UNEXPECTED_ERROR, e.getMessage());
                return new TwilioException(e.getMessage(), e);
            }
        }
    }

}
