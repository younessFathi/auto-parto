package auto.parto.service.notification;

import auto.parto.dto.NotificationRequest;
import auto.parto.service.BasicCircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Sinks;

@Service
@RequiredArgsConstructor
@Slf4j
public class ResilientNotificationService {

    private final BasicCircuitBreaker circuitBreaker;
    private final SSENotificationService sseNotificationService;
    private final PendingNotificationService pendingNotificationService;

    public boolean sendNotification(NotificationRequest notification) {
        String userId = notification.userId();
        boolean delivered = false;
        try {
            delivered = sseNotificationService.sendViaSSE(userId, notification);

            if (delivered) {
                circuitBreaker.recordSuccess(userId);
            } else {
                circuitBreaker.recordFailure(userId);
                pendingNotificationService.addPendingNotification(notification);
                // TODO: use other way to send notification
                // TODO: Think to use scheduler to send failed notifications
            }
        } catch (Exception e) {
            circuitBreaker.recordFailure(userId);
            pendingNotificationService.addPendingNotification(notification);
            // TODO: use other way to send notification
            // TODO: Think to use scheduler to send failed notifications
        }
        return delivered;
    }

    public Sinks.Many<NotificationRequest> registerConnection(String userId) {
        circuitBreaker.recordSuccess(userId);
        return sseNotificationService.registerConnection(userId);
    }

    public void unregisterConnection(String userId) {
        sseNotificationService.unregisterConnection(userId);
    }

    public boolean hasActiveConnection(String userId) {
        return sseNotificationService.hasActiveConnection(userId);
    }
}