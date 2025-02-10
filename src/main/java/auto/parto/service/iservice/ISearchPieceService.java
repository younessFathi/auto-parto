package auto.parto.service.iservice;

import auto.parto.dto.PieceDto;
import reactor.core.publisher.Mono;

public interface ISearchPieceService {
    Mono<PieceDto> notifySellers(PieceDto pieceDto);
    Mono<PieceDto> getPieceWithImagesByPieceId(String id);
    Mono<PieceDto> savePieceWithImages(PieceDto pieceDto);
}
