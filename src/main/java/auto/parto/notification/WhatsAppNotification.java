package auto.parto.notification;

import auto.parto.dto.PieceDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service("whatsAppNotification")
@Slf4j
public class WhatsAppNotification implements Notification {
    @Override
    public void notifySellers(PieceDto peiceDto) {
        log.info("Notify sellers on peice {} using whatsApp notification", peiceDto);
    }
}
