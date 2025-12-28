package auto.parto.service.iservice;

import auto.parto.dto.JwtKeyProvider;
import auto.parto.dto.SellerDto;
import auto.parto.dto.TokenDto;
import auto.parto.enums.CustomerType;
import reactor.core.publisher.Mono;

public interface OtpValidationStrategy<T extends JwtKeyProvider> {
    Mono<TokenDto<T>> validate(String otp , String email);
    CustomerType getSupportedType();
}
