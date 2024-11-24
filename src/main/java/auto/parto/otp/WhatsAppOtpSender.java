package auto.parto.otp;

import auto.parto.utils.Constants;
import com.twilio.rest.api.v2010.account.Message;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@AllArgsConstructor
@Service
public class WhatsAppOtpSender implements OtpSender {
    private OtpService otpService;

    @Override
    public void send(String phoneNumber) {
        log.info(Constants.OTPConstants.SENDING_OTP_TO, phoneNumber);
        String otp = otpService.generate(phoneNumber);
        String whatsappNumber = Constants.OTPConstants.WHATSAPP + phoneNumber;
        Message message = Message.creator(
                new com.twilio.type.PhoneNumber(whatsappNumber),
                new com.twilio.type.PhoneNumber(Constants.OTPConstants.WHATSAPP_YOUR_TWILIO_WHATSAPP_NUMBER),
                Constants.OTPConstants.YOUR_OTP_IS + otp
        ).create();
        log.info(Constants.OTPConstants.END_OF_SEND_OTP_TO, phoneNumber);
    }
}
