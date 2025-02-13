package auto.parto.notification;

import auto.parto.dto.PieceDto;
import auto.parto.dto.PieceOfferDto;
import reactor.core.publisher.Mono;

public interface Notification {
    Mono<Void> notifySellers(PieceDto pieceDto);
    Mono<PieceOfferDto> notifyCustomer(PieceOfferDto pieceOfferDto);
}
