package auto.parto.repository;

import reactor.core.publisher.Mono;

public interface IPieceRepository {
    Mono<Boolean> checkIfPieceById(String id);
}
