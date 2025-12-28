package auto.parto.otp;

import auto.parto.dto.BuyerDto;
import reactor.core.publisher.Mono;

public interface OtpValidator {
    Mono<BuyerDto> validate(BuyerDto customerDto, String otp);
}
