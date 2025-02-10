package auto.parto.notification;

import auto.parto.dto.PieceDto;
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
}
