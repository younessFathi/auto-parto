package auto.parto.repository;

import auto.parto.dto.PieceDto;
import auto.parto.model.Piece;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

public interface IPieceRepository {
    Mono<Boolean> checkIfPieceById(String id);
    Mono<Piece> save(Piece piece);
    Mono<Piece> update(String id, List<String> imagesId);
    Mono<Piece> findById(String id);
    Flux<Piece> getAllPieces(String id , int page , int size);
    Mono<Long> getTotalPieces(String customerId);
}
