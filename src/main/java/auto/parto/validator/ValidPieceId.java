package auto.parto.validator;

import auto.parto.dto.PieceOfferDto;
import auto.parto.exception.EntityNotFoundException;
import auto.parto.service.iservice.IPieceService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@AllArgsConstructor
@Slf4j
public class ValidPieceId {
    private IPieceService pieceService;

    public Mono<PieceOfferDto> isValid(PieceOfferDto pieceOfferDto) {
        String pieceId = pieceOfferDto.getPieceId();
        log.info("{} : {}", "Validating pieceId", pieceId);
            return pieceService.checkIfPieceById(pieceId).flatMap(
                    (isValid)->{
                        if(Boolean.TRUE.equals(isValid)){
                            return Mono.just(pieceOfferDto);
                        }
                        log.error("{} : {}", "Piece not exist with id", pieceId);
                        return Mono.error(new EntityNotFoundException("Piece not exist with id : "+ pieceId));
                    });
    }
}
