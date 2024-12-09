package auto.parto.otp;

import auto.parto.dto.CustomerDto;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
@Slf4j
public class WhatsAppOtpValidator implements OtpValidator {
    private final OtpService otpService;
    @Override
    public Mono<CustomerDto> validate(CustomerDto customerDto, String otp) {
        return otpService.validate(customerDto, otp);
    }
}
