package auto.parto.api;

import auto.parto.dto.OtpRequestDto;
import auto.parto.otp.OtpSender;
import auto.parto.otp.OtpValidator;
import auto.parto.utils.JwtTokenGenerator;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/auth")
@AllArgsConstructor
public class AuthController {
    private OtpSender otpSender;
    private OtpValidator otpValidator;
    private JwtTokenGenerator jwtTokenGenerator;

    @PostMapping("/send-otp")
    public Mono<String> sendOTP(@RequestBody OtpRequestDto otpRequestDto) {
        otpSender.send(otpRequestDto);
        return Mono.just("OTP sent successfully!");
    }

    @PostMapping("/validate-otp/{otp}")
    public Mono<String> validateOTP(@RequestBody OtpRequestDto otpRequestDto , @PathVariable String otp) {
        if(otpValidator.validate(otpRequestDto.getPhoneNumber(), otp)){
            // TODO: return token

            // TODO: check if customer existance on BD
            // TODO: if user not existe => create profile using full name , email , phone number
        }
        return Mono.just("OTP validated successfully!");
    }
}
