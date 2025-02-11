package auto.parto.notification;

import auto.parto.dto.PieceDto;
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
}
