package auto.parto.repository.mongo_repository;

import auto.parto.model.PieceOffer;
import auto.parto.repository.IPieceOfferRepository;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
@AllArgsConstructor
public class PieceOfferRepository implements IPieceOfferRepository {
    private ReactiveMongoTemplate reactiveMongoTemplate;

    @Override
    public Mono<PieceOffer> save(PieceOffer pieceOffer) {
        return reactiveMongoTemplate.save(pieceOffer);
    }
}
