package auto.parto.service;

import auto.parto.dto.BuyerDto;
import auto.parto.dto.TokenDto;
import auto.parto.enums.CustomerType;
import auto.parto.exception.OtpInvalidException;
import auto.parto.otp.OtpService;
import auto.parto.service.iservice.IBuyerService;
import auto.parto.service.iservice.OtpValidationStrategy;
import auto.parto.utils.Constants;
import auto.parto.utils.JwtTokenGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class BuyerOtpValidationStrategy implements OtpValidationStrategy<BuyerDto> {
    private final IBuyerService buyerService;
    private final OtpService otpService;
    private final JwtTokenGenerator jwtTokenGenerator;

    @Override
    public Mono<TokenDto<BuyerDto>> validate(String otp, String email) {
        return buyerService.findBuyerByEmail(email)
                .flatMap(user -> otpService.validate(user , otp))
                .onErrorResume(e -> Mono.error(new OtpInvalidException(Constants.Exception.INVALID_OTP)))
                .flatMap(buyerService::activateUser)
                .flatMap(user -> jwtTokenGenerator.encode(email , user , CustomerType.BUYER))
                .switchIfEmpty(Mono.error(new OtpInvalidException(Constants.Exception.INVALID_OTP)));
    }

    @Override
    public CustomerType getSupportedType() {
        return CustomerType.BUYER;
    }
}
