package auto.parto.notification;

import auto.parto.dto.PieceDto;

public interface Notification {
    void notifySellers(PieceDto peiceDto);
}
