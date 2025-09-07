package auto.parto.api;

import auto.parto.annotation.otp.ValidOtp;
import auto.parto.dto.CustomerDto;
import auto.parto.dto.OtpRequestDto;
import auto.parto.dto.TokenDto;
import auto.parto.exception.InvalidGCPToken;
import auto.parto.exception.OtpInvalidException;
import auto.parto.otp.*;
import auto.parto.service.iservice.ICustomerService;
import auto.parto.utils.Constants;
import auto.parto.utils.JwtTokenGenerator;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
@Validated
@Tag(name = Constants.Swagger.TAG_AUTH, description = Constants.Swagger.TAG_AUTH_DESCRIPTION)
@Slf4j
public class AuthController {
    private final WhatsAppOtpSender whatsAppOtpSender;
    private final EmailOtpSender emailOtpSender;
    private final OtpService otpService;
    private final JwtTokenGenerator jwtTokenGenerator;
    private final ICustomerService customerService;
    private final GoogleIdTokenVerifier googleVerifier;

    public AuthController(
            WhatsAppOtpSender whatsAppOtpSender,
            EmailOtpSender emailOtpSender,
            OtpService otpService,
            JwtTokenGenerator jwtTokenGenerator,
            ICustomerService customerService,
            @Value("${spring.security.oauth2.client.registration.google.client-id}") String gcpClientId,
            @Value("${spring.security.oauth2.client.provider.google.issuer-uri}") String url
    ){
        this.whatsAppOtpSender = whatsAppOtpSender;
        this.emailOtpSender = emailOtpSender;
        this.otpService = otpService;
        this.jwtTokenGenerator = jwtTokenGenerator;
        this.customerService = customerService;
        this.googleVerifier = new GoogleIdTokenVerifier.Builder(
                                        new NetHttpTransport(),
                                        GsonFactory.getDefaultInstance()
                                )
                                .setAudience(Collections.singletonList(gcpClientId))
                                .setIssuer(url)
                                .build();

    }


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
                                customerDto -> customerDto != null ? jwtTokenGenerator.encode(subject , customerDto):Mono.empty())
                        .switchIfEmpty(Mono.error(new OtpInvalidException(Constants.Exception.INVALID_OTP)));
    }

    @GetMapping("/checkCustomerExisting")
    public Mono<CustomerDto> loginByClerk(ServerWebExchange exchange) {
        String userId = (String) exchange.getAttribute("userId");
        String email = (String) exchange.getAttribute("email");
        String name = (String) exchange.getAttribute("name");
        String picture = (String) exchange.getAttribute("picture");
        CustomerDto customerDto = CustomerDto.builder()
                .id(UUID.randomUUID().toString())
                .email(email)
                .firstName(name)
                .phoneNumber("") // TODO: get phone number after auth
                .isActive(true)
                .picture(picture)
                .build();
        //TODO: add mapstruct for mapping
        return customerService.checkIfCustomerExistClerkId(userId)
                .flatMap(
                        exists -> {
                            if (!exists) {
                                return customerService.createCustomerFromClerkAuth(customerDto);
                            }
                            return Mono.just(customerDto);
                        }
                );
    }

    @SneakyThrows
    @PostMapping("verify-gcp-token")
    public Mono<TokenDto> verify(@RequestBody TokenDto tokenDto){
        return Mono.fromCallable(() -> verifyGCPToken(tokenDto))
                .switchIfEmpty(Mono.error(new InvalidGCPToken(Constants.Exception.INVALID_GCP_TOKEN)))
                .flatMap((GoogleIdToken idToken) -> {
                    GoogleIdToken.Payload payload = idToken.getPayload();
                    String email = payload.getEmail();
                    return jwtTokenGenerator.encode(email);
                });
        // TODO : user verification on DB
        // TODO : token management / refresh
        // TODO : token storage on cache
    }

    private GoogleIdToken verifyGCPToken(TokenDto tokenDto) {
        try {
            GoogleIdToken token = googleVerifier.verify(tokenDto.getToken());
            return token;
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException("Google token verification failed", e);
        }
    }
}
