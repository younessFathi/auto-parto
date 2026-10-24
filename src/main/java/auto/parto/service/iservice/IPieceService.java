package auto.parto.service.iservice;

import auto.parto.dto.PagedResponse;
import auto.parto.dto.PieceDto;
import reactor.core.publisher.Mono;

public interface IPieceService {
    Mono<Boolean> checkIfPieceById(String id);
    Mono<PieceDto> create(PieceDto pieceDto);
    Mono<PagedResponse<PieceDto>> getAllPiecesByCustomerId(String id, int page, int size);
    Mono<PieceDto> getPieceDetailByPieceId(String pieceId);
}
