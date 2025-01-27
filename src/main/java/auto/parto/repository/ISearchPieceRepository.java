package auto.parto.repository;

import auto.parto.model.Piece;
import reactor.core.publisher.Mono;

public interface ISearchPieceRepository {
    Mono<Piece> saveSearchPiece(Piece piece);
}
