package auto.parto.api;

import auto.parto.annotation.otp.ValidOtp;
import auto.parto.dto.CustomerDto;
import auto.parto.dto.OtpRequestDto;
import auto.parto.dto.TokenDto;
import auto.parto.exception.OtpInvalidException;
import auto.parto.otp.*;
import auto.parto.service.iservice.ICustomerService;
import auto.parto.utils.Constants;
import auto.parto.utils.JwtTokenGenerator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/auth")
@AllArgsConstructor
@Validated
@Tag(name = Constants.Swagger.TAG_AUTH, description = Constants.Swagger.TAG_AUTH_DESCRIPTION)
public class AuthController {
    private WhatsAppOtpSender whatsAppOtpSender;
    private EmailOtpSender emailOtpSender;
    private OtpService otpService;
    private JwtTokenGenerator jwtTokenGenerator;
    private ICustomerService customerService;

    @Operation(summary = Constants.Swagger.SEND_OTP_SUMMARY, description = Constants.Swagger.SEND_OTP_DESCRIPTION)
    @ApiResponses({
            @ApiResponse(responseCode = Constants.Swagger._200, description = Constants.Swagger.RESPONSE_200_OTP_SENT,
                    content = @Content(schema = @Schema(implementation = CustomerDto.class))),
            @ApiResponse(responseCode = Constants.Swagger._400, description = Constants.Swagger.RESPONSE_400_BAD_REQUEST),
            @ApiResponse(responseCode = Constants.Swagger._500, description = Constants.Swagger.RESPONSE_500_INTERNAL_ERROR)
    })
    @PostMapping("/send-otp")
    public Mono<CustomerDto> sendOTP(@Valid @RequestBody OtpRequestDto otpRequestDto) {
        return whatsAppOtpSender.send(otpRequestDto)
                        .flatMap(sentOtp -> customerService.checkIfCustomerExistUsingPhoneNumber(otpRequestDto.getPhoneNumber())
                                .flatMap(
                                    exists -> {
                                        return !exists ?
                                                customerService.create(otpRequestDto , sentOtp):
                                                customerService.updateGeneratedOtpByPhoneNumber(otpRequestDto.getPhoneNumber(), sentOtp);
                                    }
                                )
                        );
    }

    @Operation(summary = Constants.Swagger.SEND_OTP_SUMMARY, description = Constants.Swagger.SEND_OTP_DESCRIPTION)
    @ApiResponses({
            @ApiResponse(responseCode = Constants.Swagger._200, description = Constants.Swagger.RESPONSE_200_OTP_SENT,
                    content = @Content(schema = @Schema(implementation = CustomerDto.class))),
            @ApiResponse(responseCode = Constants.Swagger._400, description = Constants.Swagger.RESPONSE_400_BAD_REQUEST),
            @ApiResponse(responseCode = Constants.Swagger._500, description = Constants.Swagger.RESPONSE_500_INTERNAL_ERROR)
    })
    @PostMapping("/send-otp-email")
    public Mono<CustomerDto> sendOTPToEmail(@Valid @RequestBody OtpRequestDto otpRequestDto) {
        return emailOtpSender.send(otpRequestDto)
                .flatMap(sentOtp -> customerService.checkIfCustomerExistUsingEmail(otpRequestDto.getEmail())
                        .flatMap(
                                exists -> {
                                    return !exists ?
                                            customerService.create(otpRequestDto , sentOtp):
                                            customerService.updateGeneratedOtpByEmail(otpRequestDto.getEmail(), sentOtp);
                                }
                        )
                );
    }


    @Operation(summary = Constants.Swagger.VALIDATE_OTP_SUMMARY, description = Constants.Swagger.VALIDATE_OTP_DESCRIPTION)
    @ApiResponses({
            @ApiResponse(responseCode = Constants.Swagger._200, description =Constants.Swagger.RESPONSE_200_OTP_VALID,
                    content = @Content(schema = @Schema(implementation = TokenDto.class))),
            @ApiResponse(responseCode = Constants.Swagger._400, description = Constants.Swagger.INVALID_OTP_OR_PHONE_NUMBER),
            @ApiResponse(responseCode = Constants.Swagger._401, description = Constants.Swagger.RESPONSE_401_UNAUTHORIZED),
            @ApiResponse(responseCode = Constants.Swagger._500, description = Constants.Swagger.RESPONSE_500_INTERNAL_ERROR)
    })
    @GetMapping("/validate-otp/{otp}")
    public Mono<TokenDto> validateOTP(@ValidOtp @PathVariable String otp , @RequestParam(required = false) String phoneNumber , @RequestParam(required = false) String email) {
        if (phoneNumber == null && email == null) {
            return Mono.error(new OtpInvalidException(Constants.Exception.INVALID_OTP));
        }
        Mono<CustomerDto> customer =  phoneNumber != null ? customerService.findCustomerByPhoneNumber(phoneNumber) : customerService.findCustomerByEmail(email);
        String subject = phoneNumber != null ? phoneNumber : email;
        return customer
                        .flatMap(
                                customerDto -> otpService.validate(customerDto , otp))
                        .flatMap(
                                customerDto -> customerDto != null ? jwtTokenGenerator.generate(subject , customerDto):Mono.empty())
                        .switchIfEmpty(Mono.error(new OtpInvalidException(Constants.Exception.INVALID_OTP)));
    }
}
