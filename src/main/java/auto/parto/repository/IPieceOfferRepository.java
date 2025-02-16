package auto.parto.repository;

import auto.parto.model.PieceOffer;
import reactor.core.publisher.Mono;

import java.util.List;

public interface IPieceOfferRepository {
    Mono<PieceOffer> save(PieceOffer pieceOffer);

    Mono<PieceOffer> update(String id, List<String> imagesId);
}
