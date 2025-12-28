package auto.parto.service.iservice;

import auto.parto.dto.OtpRequestDto;
import auto.parto.enums.CustomerType;
import reactor.core.publisher.Mono;

public interface AuthStrategy {
    /**
     * Handles the logic after OTP is sent (check existence, create, or update).
     */
    Mono<Object> create(OtpRequestDto request, String sentOtp);

    /**
     * Returns the type this strategy handles.
     */
    CustomerType getSupportedType();
}
