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

@Slf4j
@Service
public class WhatsAppOtpSender implements OtpSender {
    private final OtpService otpService;

    public WhatsAppOtpSender(OtpService otpService ,
                             @Value("${twilio.sid}") String sid,
                             @Value("${twilio.auth}") String authToken) {
        this.otpService = otpService;
        Twilio.init(sid, authToken);
    }

    @Override
    public void send(OtpRequestDto otpRequestDto) {
        log.info("{} {}", Constants.OTPConstants.SENDING_OTP_TO, otpRequestDto);

        // Generate OTP and format the WhatsApp number
        String otp = otpService.generate(otpRequestDto);
        String whatsappNumber = Constants.OTPConstants.WHATSAPP + otpRequestDto;

        try {
            // Send the OTP using Twilio API
            Message message = sendWhatsAppMessage(whatsappNumber, otp);
            log.info("{} Message SID: {}", Constants.ExceptionConstants.MESSAGE_SENT, message.getSid());
        } catch (Exception e) {
            handleException(e); // Handle exceptions in a single place
        }

        log.info("{} {}", Constants.OTPConstants.END_OF_SEND_OTP_TO, otpRequestDto);
    }

    // Method to send WhatsApp message
    private Message sendWhatsAppMessage(String whatsappNumber, String otp) {
        return Message.creator(
                new PhoneNumber(whatsappNumber),
                new PhoneNumber(Constants.OTPConstants.WHATSAPP_YOUR_TWILIO_WHATSAPP_NUMBER),
                Constants.OTPConstants.YOUR_OTP_IS + otp
        ).create();
    }

    // Centralized exception handling
    private void handleException(Exception e) {
        switch (e) {
            case InvalidRequestException invalidRequestException -> {
                log.error(Constants.LOG_MESSAGE, Constants.ExceptionConstants.INVALID_REQUEST, e.getMessage());
                throw new TwilioException(e.getMessage(), e);
            }
            case AuthenticationException authenticationException -> {
                log.error(Constants.LOG_MESSAGE, Constants.ExceptionConstants.AUTHENTICATION_FAILED, e.getMessage());
                throw new TwilioException(e.getMessage(), e);
            }
            case ApiException apiException -> {
                log.error(Constants.LOG_MESSAGE, Constants.ExceptionConstants.API_ERROR, e.getMessage());
                throw new TwilioException(e.getMessage(), e);
            }
            case null, default -> {
                assert e != null;
                log.error(Constants.LOG_MESSAGE, Constants.ExceptionConstants.UNEXPECTED_ERROR, e.getMessage());
                throw new TwilioException(e.getMessage(), e);
            }
        }
    }

}
