package auto.parto.service;

import auto.parto.dto.SellerDto;
import auto.parto.dto.TokenDto;
import auto.parto.enums.CustomerType;
import auto.parto.exception.OtpInvalidException;
import auto.parto.service.iservice.ISellerService;
import auto.parto.service.iservice.OtpValidationStrategy;
import auto.parto.utils.Constants;
import auto.parto.utils.JwtTokenGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "auto.parto.persistence.postgresql.identity", name = "enabled",
        havingValue = "false", matchIfMissing = true)
public class SellerOtpValidationStrategy implements OtpValidationStrategy<SellerDto> {
    private final ISellerService sellerService;
    private final JwtTokenGenerator jwtTokenGenerator;

    @Override
    public Mono<TokenDto<SellerDto>> validate(String otp, String email) {
        return sellerService.findSellerByEmail(email)
                .flatMap(user -> {
                    return sellerService.consumeValidOtp(email, otp, LocalDateTime.now())
                            .flatMap(consumed -> consumed ? Mono.just(user)
                                    : Mono.error(new OtpInvalidException(Constants.Exception.INVALID_OTP)));
                })
                .flatMap(sellerService::activateUser)
                .flatMap(user -> jwtTokenGenerator.encode(email, user, CustomerType.SELLER))
                .onErrorResume(OtpInvalidException.class, e ->
                        sellerService.recordFailedOtpAttempt(email)
                                .then(Mono.error(new OtpInvalidException(Constants.Exception.INVALID_OTP))))
                .switchIfEmpty(Mono.error(new OtpInvalidException(Constants.Exception.INVALID_OTP)));
    }

    @Override
    public CustomerType getSupportedType() {
        return CustomerType.SELLER;
    }
}
