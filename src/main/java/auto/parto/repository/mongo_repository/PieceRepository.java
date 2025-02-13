package auto.parto.repository.mongo_repository;

import auto.parto.model.Piece;
import auto.parto.repository.IPieceRepository;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
@AllArgsConstructor
public class PieceRepository implements IPieceRepository {
    private final ReactiveMongoTemplate reactiveMongoTemplate;

    @Override
    public Mono<Boolean> checkIfPieceById(String id) {
        return reactiveMongoTemplate.findById(id , Piece.class).hasElement();
    }
}
