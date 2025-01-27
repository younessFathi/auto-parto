package auto.parto.repository.mongo_repository;

import auto.parto.dto.PieceDto;
import auto.parto.mapper.PieceMapper;
import auto.parto.model.Piece;
import auto.parto.repository.ISearchPieceRepository;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
@AllArgsConstructor
public class SearchPieceRepository implements ISearchPieceRepository {
    private final ReactiveMongoTemplate reactiveMongoTemplate;

    @Override
    public Mono<Piece> saveSearchPiece(Piece piece) {
        return reactiveMongoTemplate.save(piece);
    }
}
