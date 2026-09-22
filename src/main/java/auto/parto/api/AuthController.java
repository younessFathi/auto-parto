package auto.parto.api;

import auto.parto.dto.*;
import auto.parto.enums.CustomerType;
import auto.parto.otp.*;
import auto.parto.service.iservice.AuthStrategy;
import auto.parto.service.iservice.OtpValidationStrategy;
import auto.parto.utils.Constants;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/auth")
@Validated
@Tag(name = Constants.Swagger.TAG_AUTH, description = Constants.Swagger.TAG_AUTH_DESCRIPTION)
@Slf4j
public class AuthController {
    private final EmailService emailService;
    // Map to hold strategies: Key = CustomerType, Value = Strategy Implementation
    private final Map<CustomerType, AuthStrategy> authStrategies;
    private final Map<CustomerType, OtpValidationStrategy<?>> otpValidationStrategies;
    private final OtpService otpService;


    public AuthController(
            EmailService emailService,
            List<AuthStrategy> strategies,
            List<OtpValidationStrategy<?>> otpValidationStrategies,
            OtpService otpService){
        this.emailService = emailService;
        this.authStrategies = strategies.stream()
                .collect(Collectors.toMap(AuthStrategy::getSupportedType, Function.identity()));
        this.otpValidationStrategies = otpValidationStrategies.stream()
                .collect(Collectors.toMap(OtpValidationStrategy::getSupportedType, Function.identity()));
        this.otpService = otpService;
    }

    @PostMapping("/send-otp-email")
    public Mono<Object> sendOTPToEmail(@Valid @RequestBody OtpRequestDto otpRequestDto) {
        return emailService.sendOTPEmail(otpRequestDto.getEmail(), otpService.generate())
                .flatMap(sentOtp -> {
                            AuthStrategy strategy = authStrategies.get(otpRequestDto.getCustomerType());
                            if (strategy == null) {
                                return Mono.error(new IllegalArgumentException("Unsupported customer type: " + otpRequestDto.getCustomerType()));
                            }
                            return strategy.create(otpRequestDto, sentOtp);
                });
    }


    @PostMapping("/validate-otp")
    public Mono<TokenDto<?>> validateOTP(@RequestBody ValidateOtpRequest validateOtpRequest) {
        OtpValidationStrategy<?> strategy = otpValidationStrategies.get(validateOtpRequest.getCustomerType());
        if (strategy == null) {
            return Mono.error(new IllegalArgumentException("Unsupported customer type: " + validateOtpRequest.getCustomerType()));
        }
        return strategy.validate(validateOtpRequest.getOtp(), validateOtpRequest.getEmail()).map(token -> (TokenDto<?>) token);
    }

}
