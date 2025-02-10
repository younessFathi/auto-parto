package auto.parto.notification;

import auto.parto.dto.PieceDto;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service("emailNotification")
public class EmailNotification implements Notification {
    @Override
    public Mono<Void> notifySellers(PieceDto peiceDto) {
        return Mono.empty();
    }
}
