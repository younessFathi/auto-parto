package auto.parto.service;

import auto.parto.dto.OtpDetailsDto;
import auto.parto.dto.SellerDto;
import auto.parto.dto.TokenDto;
import auto.parto.enums.CustomerType;
import auto.parto.exception.OtpInvalidException;
import auto.parto.exception.OtpLockedException;
import auto.parto.otp.OtpService;
import auto.parto.service.iservice.ISellerService;
import auto.parto.service.iservice.OtpValidationStrategy;
import auto.parto.utils.Constants;
import auto.parto.utils.JwtTokenGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class SellerOtpValidationStrategy implements OtpValidationStrategy<SellerDto> {
    private final ISellerService sellerService;
    private final OtpService otpService;
    private final JwtTokenGenerator jwtTokenGenerator;

    @Override
    public Mono<TokenDto<SellerDto>> validate(String otp, String email) {
        return sellerService.findSellerByEmail(email)
                .flatMap(user -> {
                    OtpDetailsDto otpDetails = user.getOtpDetailsDto();
                    if (otpDetails != null && otpDetails.isLocked()) {
                        return Mono.error(new OtpLockedException("Account is locked due to too many failed attempts"));
                    }
                    return otpService.validate(user, otp);
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
