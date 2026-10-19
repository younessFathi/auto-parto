package auto.parto.service;

import auto.parto.dto.OtpRequestDto;
import auto.parto.enums.CustomerType;
import auto.parto.service.iservice.AuthStrategy;
import auto.parto.service.iservice.IBuyerService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@ConditionalOnProperty(prefix = "auto.parto.persistence.postgresql.buyer-identity", name = "enabled",
        havingValue = "false", matchIfMissing = true)
@RequiredArgsConstructor
public class BuyerAuthStrategy implements AuthStrategy {
    private final IBuyerService buyerService;
    @Override
    public Mono<Object> create(OtpRequestDto request, String sentOtp) {
        return buyerService.checkIfBuyerExistUsingEmail(request.getEmail())
                .flatMap(exists -> exists
                        ? buyerService.updateGeneratedOtpByEmail(request.getEmail(), sentOtp)
                        : buyerService.create(request, sentOtp))
                .map(buyerDto -> (Object) buyerDto); // Cast to Object or common interface
    }

    @Override
    public CustomerType getSupportedType() {
        return CustomerType.BUYER;
    }
}
