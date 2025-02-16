package auto.parto.service.iservice;

import auto.parto.dto.PieceDto;
import reactor.core.publisher.Mono;

public interface IPieceService {
    Mono<Boolean> checkIfPieceById(String id);
    Mono<PieceDto> create(PieceDto pieceDto);
    Mono<PieceDto> update(PieceDto pieceDto);
}
