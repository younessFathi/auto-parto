package auto.parto.repository.mongo_repository;

import auto.parto.model.Piece;
import auto.parto.repository.ISearchPieceRepository;
import lombok.AllArgsConstructor;
import org.bson.types.Binary;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.Base64;

@Repository
@AllArgsConstructor
public class SearchPieceRepository implements ISearchPieceRepository {
    private final ReactiveMongoTemplate reactiveMongoTemplate;

    @Override
    public Mono<Piece> saveSearchPiece(Piece piece) {
        return reactiveMongoTemplate.save(piece);
    }

    @Override
    public Mono<Piece> getPieceById(String id) {
        return reactiveMongoTemplate.findById(id, Piece.class);
    }
}
