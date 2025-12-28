package auto.parto.validator;

import auto.parto.dto.PieceDto;
import auto.parto.dto.PieceOfferDto;
import auto.parto.exception.EntityNotFoundException;
import auto.parto.service.iservice.IBuyerService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@AllArgsConstructor
@Slf4j
public class ValidCustomerId {
    private final IBuyerService customerService;

    public Mono<PieceDto> isValid(PieceDto pieceDto) {
        return validateCustomerId(pieceDto.getCustomerId())
                .thenReturn(pieceDto);
    }

    public Mono<PieceOfferDto> isValid(PieceOfferDto pieceOfferDto) {
        return validateCustomerId(pieceOfferDto.getCustomerId())
                .thenReturn(pieceOfferDto);
    }

    private Mono<Void> validateCustomerId(String customerId) {
        log.info("{} : {}", "Validating customerId", customerId);
        return customerService.checkIfBuyerExistUsingId(customerId)
                .flatMap(isValid -> {
                    if (Boolean.TRUE.equals(isValid)) {
                        return Mono.empty();
                    } else {
                        log.error("{} : {}", "Buyer not exist with id", customerId);
                        return Mono.error(new EntityNotFoundException("Buyer not exist with id: " + customerId));
                    }
                });
    }
}
