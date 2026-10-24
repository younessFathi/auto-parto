package auto.parto.service.notification;

import auto.parto.dto.NotificationRequest;
import auto.parto.persistence.NotificationRecord;
import auto.parto.service.BasicCircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ResilientNotificationService {

    private final BasicCircuitBreaker circuitBreaker;
    private final SSENotificationService sseNotificationService;
    private final HybridNotificationStore notificationStore;
    private final String directWorkerId = "notification-direct-" + UUID.randomUUID();

    public record DirectDeliveryResult(UUID publicId, boolean persisted, boolean delivered) {
    }

    public Mono<Boolean> sendNotification(NotificationRequest notification) {
        return deliverDirect(notification).map(DirectDeliveryResult::delivered);
    }

    public Mono<DirectDeliveryResult> deliverDirect(NotificationRequest notification) {
        String userId = notification.userId();
        return notificationStore.storePendingRecord(notification)
                .flatMap(record -> notificationStore.claimNotification(record.publicId(), directWorkerId,
                                Instant.now(), Duration.ofSeconds(30))
                        .flatMap(claimed -> deliverClaimed(userId, notification, claimed))
                        .defaultIfEmpty(new DirectDeliveryResult(record.publicId(), true, false)))
                .defaultIfEmpty(new DirectDeliveryResult(null, false, false))
                .doOnNext(result -> {
                    if (result.delivered()) circuitBreaker.recordSuccess(userId);
                    else circuitBreaker.recordFailure(userId);
                })
                .onErrorResume(error -> {
                    circuitBreaker.recordFailure(userId);
                    return Mono.just(new DirectDeliveryResult(null, false, false));
                });
    }

    private Mono<DirectDeliveryResult> deliverClaimed(String userId, NotificationRequest notification,
                                                       NotificationRecord claimed) {
        return Mono.defer(() -> {
            boolean delivered = sseNotificationService.sendViaSSE(userId, claimed.publicId(), notification);
            if (delivered) {
                return notificationStore.recordAttempt(claimed.publicId(), "SSE", "WRITTEN", null, null)
                        .then(notificationStore.acknowledgeDelivery(claimed.publicId(), directWorkerId))
                        .map(acknowledged -> new DirectDeliveryResult(claimed.publicId(), true, acknowledged));
            }
            return notificationStore.recordAttempt(claimed.publicId(), "SSE", "FAILED", null,
                            "DELIVERY_UNAVAILABLE")
                    .then(notificationStore.scheduleRetry(claimed.publicId(), directWorkerId,
                            Instant.now().plusSeconds(30), "DELIVERY_UNAVAILABLE", 3))
                    .thenReturn(new DirectDeliveryResult(claimed.publicId(), true, false));
        }).onErrorResume(error -> notificationStore.recordAttempt(claimed.publicId(), "SSE", "FAILED", null,
                        "DELIVERY_ERROR")
                .thenReturn(new DirectDeliveryResult(claimed.publicId(), true, false)));
    }

    public Sinks.Many<SseNotificationEvent> registerConnection(String userId) {
        circuitBreaker.recordSuccess(userId);
        return sseNotificationService.registerConnection(userId);
    }

    public void unregisterConnection(String userId) {
        sseNotificationService.unregisterConnection(userId);
    }

    public void unregisterConnection(String userId, Sinks.Many<SseNotificationEvent> expectedConnection) {
        sseNotificationService.unregisterConnection(userId, expectedConnection);
    }

    public boolean hasActiveConnection(String userId) {
        return sseNotificationService.hasActiveConnection(userId);
    }
}
