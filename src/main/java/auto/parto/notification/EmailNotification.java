package auto.parto.notification;

import auto.parto.dto.PieceDto;
import org.springframework.stereotype.Service;

@Service("emailNotification")
public class EmailNotification implements Notification {
    @Override
    public void notifySellers(PieceDto peiceDto) {
    }
}
