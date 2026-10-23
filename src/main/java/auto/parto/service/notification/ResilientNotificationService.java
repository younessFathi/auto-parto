package auto.parto.service.notification;

import auto.parto.dto.NotificationRequest;
import auto.parto.service.BasicCircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

@Service
@RequiredArgsConstructor
@Slf4j
public class ResilientNotificationService {

    private final BasicCircuitBreaker circuitBreaker;
    private final SSENotificationService sseNotificationService;

    public Mono<Boolean> sendNotification(NotificationRequest notification) {
        String userId = notification.userId();
        return Mono.defer(() -> {
            boolean delivered = sseNotificationService.sendViaSSE(userId, notification);
            if (delivered) {
                circuitBreaker.recordSuccess(userId);
                return Mono.just(true);
            }
            circuitBreaker.recordFailure(userId);
            return Mono.just(false);
        }).onErrorResume(error -> {
            circuitBreaker.recordFailure(userId);
            return Mono.just(false);
        });
    }

    public Sinks.Many<NotificationRequest> registerConnection(String userId) {
        circuitBreaker.recordSuccess(userId);
        return sseNotificationService.registerConnection(userId);
    }

    public void unregisterConnection(String userId) {
        sseNotificationService.unregisterConnection(userId);
    }

    public void unregisterConnection(String userId, Sinks.Many<NotificationRequest> expectedConnection) {
        sseNotificationService.unregisterConnection(userId, expectedConnection);
    }

    public boolean hasActiveConnection(String userId) {
        return sseNotificationService.hasActiveConnection(userId);
    }
}
