package auto.parto.repository.mongo_repository;

import auto.parto.dto.PieceDto;
import auto.parto.model.Piece;
import auto.parto.repository.IPieceRepository;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.List;

@Repository
@AllArgsConstructor
public class PieceRepository implements IPieceRepository {
    private final ReactiveMongoTemplate reactiveMongoTemplate;

    @Override
    public Mono<Boolean> checkIfPieceById(String id) {
        return findById(id).hasElement();
    }

    @Override
    public Mono<Piece> save(Piece piece) {
        return reactiveMongoTemplate.save(piece);
    }

    @Override
    public Mono<Piece> update(String id, List<String> imagesId) {
        return findById(id).map(piece -> {
            piece.setImagesId(imagesId);
            return piece;
        });
    }

    @Override
    public Mono<Piece> findById(String id) {
        return reactiveMongoTemplate.findById(id , Piece.class);
    }
}
