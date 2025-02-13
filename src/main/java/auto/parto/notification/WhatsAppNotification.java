package auto.parto.notification;

import auto.parto.dto.PieceDto;
import auto.parto.dto.PieceOfferDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service("whatsAppNotification")
@Slf4j
public class WhatsAppNotification implements Notification {
    @Override
    public Mono<Void> notifySellers(PieceDto pieceDto) {
        log.info("Notify sellers on piece {} using whatsApp notification", pieceDto);
        return Mono.empty();
    }

    @Override
    public Mono<PieceOfferDto> notifyCustomer(PieceOfferDto pieceOfferDto) {
        String customerId = pieceOfferDto.getCustomerId();
        String pieceId = pieceOfferDto.getPieceId();
        log.info("Notify customer {} on offer {} using whatsApp notification", customerId , pieceId);
        return null;
    }
}
