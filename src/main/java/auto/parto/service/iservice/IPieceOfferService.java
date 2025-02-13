package auto.parto.service.iservice;

import auto.parto.dto.PieceOfferDto;
import reactor.core.publisher.Mono;

public interface IPieceOfferService {
    Mono<PieceOfferDto> save(PieceOfferDto pieceOfferDto);
}
