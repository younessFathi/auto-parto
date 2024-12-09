package auto.parto.otp;

import auto.parto.dto.CustomerDto;
import reactor.core.publisher.Mono;

public interface OtpValidator {
    Mono<CustomerDto> validate(CustomerDto customerDto, String otp);
}
