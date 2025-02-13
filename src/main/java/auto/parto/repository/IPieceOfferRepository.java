package auto.parto.repository;

import auto.parto.model.PieceOffer;
import reactor.core.publisher.Mono;

public interface IPieceOfferRepository {
    Mono<PieceOffer> save(PieceOffer pieceOffer);
}
