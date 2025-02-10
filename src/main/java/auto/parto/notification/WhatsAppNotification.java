package auto.parto.notification;

import auto.parto.dto.PieceDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service("whatsAppNotification")
@Slf4j
public class WhatsAppNotification implements Notification {
    @Override
    public Mono<Void> notifySellers(PieceDto peiceDto) {
        log.info("Notify sellers on piece {} using whatsApp notification", peiceDto);
        return Mono.empty();
    }
}
