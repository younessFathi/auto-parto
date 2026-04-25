package auto.parto.repository.mongo_repository;

import auto.parto.model.PieceOffer;
import auto.parto.repository.IPieceOfferRepository;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.List;

@Repository
@AllArgsConstructor
public class PieceOfferRepository implements IPieceOfferRepository {
    private ReactiveMongoTemplate reactiveMongoTemplate;

    @Override
    public Mono<PieceOffer> save(PieceOffer pieceOffer) {
        return reactiveMongoTemplate.save(pieceOffer);
    }

    @Override
    public Mono<PieceOffer> update(String id, List<String> imagesId) {
        Query query = new Query(Criteria.where("_id").is(id));
        Update update = new Update().set("imagesId", imagesId);
        FindAndModifyOptions options = FindAndModifyOptions.options().returnNew(true);
        return reactiveMongoTemplate.findAndModify(query, update, options, PieceOffer.class);
    }
}
