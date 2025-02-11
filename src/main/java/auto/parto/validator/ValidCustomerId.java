package auto.parto.validator;

import auto.parto.dto.PieceDto;
import auto.parto.exception.EntityNotFoundException;
import auto.parto.service.iservice.ICustomerService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@AllArgsConstructor
@Slf4j
public class ValidCustomerId {
    private final ICustomerService customerService;

    public Mono<PieceDto> isValid(PieceDto pieceDto){
        log.info("{} : {} " , "Validating customerId" ,pieceDto.getCustomerId());
        String customerId = pieceDto.getCustomerId();
        return customerService.checkIfCustomerExistUsingId(customerId).flatMap(
                isValid -> {
                    if (Boolean.TRUE.equals(isValid))
                        return Mono.just(pieceDto);
                    log.error("{} : {}" , "Customer not exist with id" , customerId);
                    return Mono.error(new EntityNotFoundException("Customer not exist with id : "+ customerId));
                }
        );
    }
}
