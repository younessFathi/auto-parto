package auto.parto.otp;

import auto.parto.dto.OtpRequestDto;
import auto.parto.utils.Constants;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
@Slf4j
public class Sender {
    private OtpService otpService;

    Mono<String> send(String to, SenderFunction<String , String , String> sender) {
        return otpService.generate()
                .flatMap(
                        otp -> {
                            log.info("{} : {}", Constants.OTPConstants.GENERATED_OTP, otp);
                            return sender.send(to, otp)
                                    .flatMap(
                                            (output) -> {
                                                log.info("{}" , output);
                                                return Mono.just(otp);
                                            }
                                    )
                                    .doOnTerminate(()->log.info("{} : {}", Constants.OTPConstants.END_OF_SEND_OTP_TO, to));
                        }
                );
    }
}
