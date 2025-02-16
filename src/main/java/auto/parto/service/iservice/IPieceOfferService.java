package auto.parto.service.iservice;

import auto.parto.dto.PieceOfferDto;
import reactor.core.publisher.Mono;

public interface IPieceOfferService {
    Mono<PieceOfferDto> save(PieceOfferDto pieceOfferDto);
    Mono<PieceOfferDto> create(PieceOfferDto pieceOfferDto);
    Mono<PieceOfferDto> update(PieceOfferDto pieceOfferDto);
}
