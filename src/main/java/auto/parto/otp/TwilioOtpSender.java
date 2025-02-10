package auto.parto.otp;

import auto.parto.utils.Constants;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class TwilioOtpSender {

    public TwilioOtpSender(
            @Value("${twilio.sid}") String sid,
            @Value("${twilio.auth}") String authToken) {
        Twilio.init(sid, authToken);
    }

    public Mono<String> send(String whatsappNumber, String otp) {
        return Mono.defer(
                ()->{
                    try{
                        Message message = Message.creator(
                                new PhoneNumber(whatsappNumber),
                                new PhoneNumber(Constants.OTPConstants.WHATSAPP_YOUR_TWILIO_WHATSAPP_NUMBER),
                                Constants.OTPConstants.YOUR_OTP_IS + otp
                        ).create();
                        return Mono.just(message.getSid());
                    }catch(Exception e) {
                        return Mono.error(e);
                    }
        });
    }
}
