package auto.parto.service.iservice;

import auto.parto.dto.PieceDto;
import auto.parto.dto.PieceOfferDto;
import auto.parto.dto.PieceWithFilesDto;
import jakarta.validation.Valid;
import reactor.core.publisher.Mono;

public interface ISearchPieceService {
    Mono<PieceDto> notifySellers(PieceDto pieceDto);
    Mono<PieceWithFilesDto> getPieceWithImagesByPieceId(String id);
    Mono<PieceDto> savePieceWithImages(PieceDto pieceDto);
    Mono<PieceOfferDto> notifyCustomer(@Valid PieceOfferDto pieceOfferDto);
    Mono<PieceOfferDto> createOffer(PieceOfferDto pieceOfferDto);
    Mono<Boolean> checkIfPieceById(String id);
}
