package auto.parto.otp;

import auto.parto.dto.OtpRequestDto;
import auto.parto.utils.Constants;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@AllArgsConstructor
@Slf4j
public class EmailOtpSender implements OtpSender{
    private EmailService emailService;
    private Sender sender;

    @Override
    public Mono<String> send(OtpRequestDto otpRequestDto) {
        log.info("{} : {}", Constants.OTPConstants.SENDING_OTP_TO, otpRequestDto.getEmail());
        return sender.send(otpRequestDto.getEmail(), emailService::sendOTPEmail);
    }
}
