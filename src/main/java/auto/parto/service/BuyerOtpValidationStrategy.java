package auto.parto.service;

import auto.parto.dto.BuyerDto;
import auto.parto.dto.OtpDetailsDto;
import auto.parto.dto.TokenDto;
import auto.parto.enums.CustomerType;
import auto.parto.exception.OtpInvalidException;
import auto.parto.exception.OtpLockedException;
import auto.parto.service.iservice.IBuyerService;
import auto.parto.service.iservice.OtpValidationStrategy;
import auto.parto.utils.Constants;
import auto.parto.utils.JwtTokenGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Service
@ConditionalOnProperty(prefix = "auto.parto.persistence.postgresql.buyer-identity", name = "enabled",
        havingValue = "false", matchIfMissing = true)
@RequiredArgsConstructor
public class BuyerOtpValidationStrategy implements OtpValidationStrategy<BuyerDto> {
    private final IBuyerService buyerService;
    private final JwtTokenGenerator jwtTokenGenerator;

    @Override
    public Mono<TokenDto<BuyerDto>> validate(String otp, String email) {
        return buyerService.findBuyerByEmail(email)
                .flatMap(user -> {
                    OtpDetailsDto otpDetails = user.getOtpDetailsDto();
                    if (otpDetails != null && otpDetails.isLocked()) {
                        return Mono.error(new OtpLockedException("Account is locked due to too many failed attempts"));
                    }
                    return buyerService.consumeValidOtp(email, otp, LocalDateTime.now())
                            .flatMap(consumed -> consumed ? Mono.just(user)
                                    : Mono.error(new OtpInvalidException(Constants.Exception.INVALID_OTP)));
                })
                .flatMap(buyerService::activateUser)
                .flatMap(user -> jwtTokenGenerator.encode(email, user, CustomerType.BUYER))
                .onErrorResume(OtpInvalidException.class, e ->
                        buyerService.recordFailedOtpAttemptByEmail(email)
                                .then(Mono.error(new OtpInvalidException(Constants.Exception.INVALID_OTP))))
                .switchIfEmpty(Mono.error(new OtpInvalidException(Constants.Exception.INVALID_OTP)));
    }

    @Override
    public CustomerType getSupportedType() {
        return CustomerType.BUYER;
    }
}
