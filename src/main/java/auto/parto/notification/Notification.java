package auto.parto.notification;

import auto.parto.dto.PieceDto;
import reactor.core.publisher.Mono;

public interface Notification {
    Mono<Void> notifySellers(PieceDto pieceDto);
}
