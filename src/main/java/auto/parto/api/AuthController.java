package auto.parto.api;

import auto.parto.dto.*;
import auto.parto.enums.CustomerType;
import auto.parto.exception.InvalidGCPToken;
import auto.parto.otp.*;
import auto.parto.service.iservice.AuthStrategy;
import auto.parto.service.iservice.IBuyerService;
import auto.parto.service.iservice.OtpValidationStrategy;
import auto.parto.utils.Constants;
import auto.parto.utils.JwtTokenGenerator;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/auth")
@Validated
@Tag(name = Constants.Swagger.TAG_AUTH, description = Constants.Swagger.TAG_AUTH_DESCRIPTION)
@Slf4j
public class AuthController {
    private final EmailService emailService;
    private final JwtTokenGenerator jwtTokenGenerator;
    private final IBuyerService buyerService;
    private final GoogleIdTokenVerifier googleVerifier;
    // Map to hold strategies: Key = CustomerType, Value = Strategy Implementation
    private final Map<CustomerType, AuthStrategy> authStrategies;
    private final Map<CustomerType, OtpValidationStrategy<?>> otpValidationStrategies;
    private final OtpService otpService;


    public AuthController(
            EmailService emailService,
            JwtTokenGenerator jwtTokenGenerator,
            IBuyerService buyerService,
            List<AuthStrategy> strategies,
            List<OtpValidationStrategy<?>> otpValidationStrategies,
            @Value("${spring.security.oauth2.client.registration.google.client-id}") String gcpClientId,
            @Value("${spring.security.oauth2.client.provider.google.issuer-uri}") String url,
            OtpService otpService){
        this.emailService = emailService;
        this.jwtTokenGenerator = jwtTokenGenerator;
        this.buyerService = buyerService;
        this.googleVerifier = new GoogleIdTokenVerifier.Builder(
                                        new NetHttpTransport(),
                                        GsonFactory.getDefaultInstance()
                                )
                                .setAudience(Collections.singletonList(gcpClientId))
                                .setIssuer(url)
                                .build();
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

    @GetMapping("/checkCustomerExisting")
    public Mono<BuyerDto> loginByClerk(ServerWebExchange exchange) {
        String userId = (String) exchange.getAttribute("userId");
        String email = (String) exchange.getAttribute("email");
        String name = (String) exchange.getAttribute("name");
        String picture = (String) exchange.getAttribute("picture");
        BuyerDto customerDto = BuyerDto.builder()
                .id(UUID.randomUUID().toString())
                .email(email)
                .firstName(name)
                .phoneNumber("") // TODO: get phone number after auth
                .isActive(true)
                .picture(picture)
                .build();
        //TODO: add mapstruct for mapping
        return buyerService.checkIfBuyerExistClerkId(userId)
                .flatMap(
                        exists -> {
                            if (!exists) {
                                return buyerService.createBuyerFromClerkAuth(customerDto);
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
