package auto.parto.service;

import auto.parto.dto.OtpRequestDto;
import auto.parto.enums.CustomerType;
import auto.parto.service.iservice.AuthStrategy;
import auto.parto.service.iservice.ISellerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class SellerAuthStrategy implements AuthStrategy {
    private final ISellerService sellerService; // Assuming you have this
    @Override
    public Mono<Object> create(OtpRequestDto request, String sentOtp) {
        return sellerService.checkIfSellerExistUsingEmail(request.getEmail())
                .flatMap(exists -> !exists ?
                        sellerService.create(request, sentOtp) :
                        sellerService.updateGeneratedOtpByEmail(request.getEmail(), sentOtp)
                );
    }

    @Override
    public CustomerType getSupportedType() {
        return CustomerType.SELLER;
    }
}
