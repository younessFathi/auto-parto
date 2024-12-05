package auto.parto.otp;

import auto.parto.dto.OtpRequestDto;
import reactor.core.publisher.Mono;

public interface OtpSender {
    Mono<String> send(OtpRequestDto otpRequestDto);
}
