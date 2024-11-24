package auto.parto.api;

import auto.parto.otp.WhatsAppOtpSender;
import auto.parto.otp.WhatsAppOtpValidator;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/auth")
@AllArgsConstructor
public class AuthController {
    private WhatsAppOtpSender whatsAppOtpSender;
    private WhatsAppOtpValidator whatsAppOtpValidator;

    @GetMapping("/send-otp/{phoneNumber}")
    public Mono<String> sendOTP(@PathVariable String phoneNumber) {
        whatsAppOtpSender.send(phoneNumber);
        return Mono.just("OTP sent successfully!");
    }

    @GetMapping("/validate-otp/{phoneNumber}/{otp}")
    public Mono<String> validateOTP(@PathVariable String phoneNumber, @PathVariable String otp) {
        whatsAppOtpValidator.validate(phoneNumber, otp);

        return Mono.just("OTP validated successfully!");
    }
}
