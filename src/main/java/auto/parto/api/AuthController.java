package auto.parto.api;

import auto.parto.annotation.otp.ValidOtp;
import auto.parto.annotation.phoneNumber.ValidPhoneNumber;
import auto.parto.dto.CustomerDto;
import auto.parto.dto.OtpRequestDto;
import auto.parto.exception.OtpInvalidException;
import auto.parto.otp.OtpSender;
import auto.parto.otp.OtpService;
import auto.parto.otp.OtpValidator;
import auto.parto.service.iservice.ICustomerService;
import auto.parto.utils.Constants;
import auto.parto.utils.JwtTokenGenerator;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/auth")
@AllArgsConstructor
@Validated
public class AuthController {
    private OtpSender otpSender;
    private OtpValidator otpValidator;
    private JwtTokenGenerator jwtTokenGenerator;
    private OtpService otpService;
    private ICustomerService customerService;

    @PostMapping("/send-otp")
    public Mono<CustomerDto> sendOTP(@Valid @RequestBody OtpRequestDto otpRequestDto) {
        return otpSender.send(otpRequestDto)
                        .flatMap(sentOtp -> customerService.checkIfCustomerExistUsingPhoneNumber(otpRequestDto.getPhoneNumber())
                                .flatMap(
                                    exists -> {
                                        return !exists ?
                                                customerService.create(otpRequestDto , sentOtp):
                                                customerService.updateGeneratedOtp(otpRequestDto.getPhoneNumber(), sentOtp);
                                    }
                                )
                        );
    }

    @GetMapping("/validate-otp/{otp}/{phoneNumber}")
    public Mono<String> validateOTP(@ValidOtp @PathVariable String otp , @ValidPhoneNumber @PathVariable String phoneNumber) {
        return customerService.findCustomerByPhoneNumber(phoneNumber)
                        .flatMap(
                                customerDto -> otpValidator.validate(customerDto , otp))
                        .flatMap(
                                jwtKey -> jwtKey != null ? jwtTokenGenerator.generate(phoneNumber , jwtKey):Mono.empty())
                        .switchIfEmpty(Mono.error(new OtpInvalidException(Constants.ExceptionConstants.INVALID_OTP)));
    }
}
