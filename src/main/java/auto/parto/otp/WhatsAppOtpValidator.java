package auto.parto.otp;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
@Slf4j
public class WhatsAppOtpValidator implements OtpValidator {
    private final OtpService otpService;
    @Override
    public Boolean validate(String phoneNumber , String otp) {
        return otpService.validate(phoneNumber , otp);
    }
}
