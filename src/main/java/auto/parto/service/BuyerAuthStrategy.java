package auto.parto.service;

import auto.parto.dto.OtpRequestDto;
import auto.parto.enums.CustomerType;
import auto.parto.service.iservice.AuthStrategy;
import auto.parto.service.iservice.IBuyerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class BuyerAuthStrategy implements AuthStrategy {
    private final IBuyerService buyerService;
    @Override
    public Mono<Object> create(OtpRequestDto request, String sentOtp) {
        return buyerService.checkIfBuyerExistUsingPhoneNumber(request.getPhoneNumber())
                .flatMap(exists -> !exists ?
                        buyerService.create(request, sentOtp) :
                        buyerService.updateGeneratedOtpByPhoneNumber(request.getPhoneNumber(), sentOtp)
                )
                .map(buyerDto -> (Object) buyerDto); // Cast to Object or common interface
    }

    @Override
    public CustomerType getSupportedType() {
        return CustomerType.BUYER;
    }
}
