package auto.parto.notification;

import auto.parto.dto.PieceDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service("pushNotification")
@Slf4j
public class PushNotification implements Notification {
    @Override
    public void notifySellers(PieceDto peiceDto) {
        // using Firebase cloud messaging
        log.info("Notify sellers on peice {} using push notification", peiceDto);
    }
}
