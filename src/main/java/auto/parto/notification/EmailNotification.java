package auto.parto.notification;

import auto.parto.dto.PieceDto;
import auto.parto.dto.PieceOfferDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service("emailNotification")
@Slf4j
public class EmailNotification implements Notification {
    @Override
    public Mono<Void> notifySellers(PieceDto pieceDto) {
        log.info("Notify sellers on piece {} using email notification", pieceDto);
        return Mono.empty();
    }

    @Override
    public Mono<PieceOfferDto> notifyCustomer(PieceOfferDto pieceOfferDto) {
        String customerId = pieceOfferDto.getCustomerId();
        String pieceId = pieceOfferDto.getPieceId();
        log.info("Notify customer {} on offer {} using email notification", customerId , pieceId);
        return Mono.just(pieceOfferDto);
    }
}
