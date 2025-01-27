package auto.parto.service.iservice;

import auto.parto.dto.PieceDto;
import reactor.core.publisher.Mono;

public interface ISearchPieceService {
    Mono<PieceDto> searchPieceAndNotifySellers(PieceDto pieceDto);
}
