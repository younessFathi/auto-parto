package auto.parto.service.notification;

import auto.parto.dto.NotificationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class PendingNotificationService {

    private final HybridNotificationStore hybridNotificationStore;

    /**
     * Persists notification intent in PostgreSQL and updates Redis only as a best-effort projection.
     */
    public Mono<Boolean> addPendingNotification(NotificationRequest notification) {
        return hybridNotificationStore.storePending(notification);
    }
}
