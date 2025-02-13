package auto.parto.notification;

import auto.parto.dto.PieceDto;
import auto.parto.dto.PieceOfferDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service("pushNotification")
@Slf4j
public class PushNotification implements Notification {
    @Override
    public Mono<Void> notifySellers(PieceDto peiceDto) {
        // using Firebase cloud messaging
        log.info("Notify sellers on piece {} using push notification", peiceDto);
        return Mono.empty();
    }

    @Override
    public Mono<PieceOfferDto> notifyCustomer(PieceOfferDto pieceOfferDto) {
        String customerId = pieceOfferDto.getCustomerId();
        String pieceId = pieceOfferDto.getPieceId();
        log.info("Notify customer {} on offer {} using push notification", customerId , pieceId);
        return Mono.just(pieceOfferDto);
    }
}
