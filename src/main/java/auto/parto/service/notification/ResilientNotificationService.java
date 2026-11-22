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

    public record DirectDeliveryResult(UUID publicId, boolean persisted, boolean delivered, boolean suppressed) {
        public DirectDeliveryResult(UUID publicId, boolean persisted, boolean delivered) {
            this(publicId, persisted, delivered, false);
        }
    }

    public Mono<Boolean> sendNotification(NotificationRequest notification) {
        return deliverDirect(notification).map(DirectDeliveryResult::delivered);
    }

    public Mono<DirectDeliveryResult> deliverDirect(NotificationRequest notification) {
        NotificationPolicy.category(notification.type() == null ? null : notification.type().name());
        NotificationRequest canonical = NotificationPolicy.canonicalRecipient(notification);
        String userId = canonical.userId();
        String claimOwner = directWorkerId + "-" + UUID.randomUUID();
        return notificationStore.storePendingRecord(canonical)
                .flatMap(record -> notificationStore.claimNotification(record.publicId(), claimOwner,
                                Instant.now(), Duration.ofSeconds(30))
                        .flatMap(claimed -> deliverClaimed(userId, canonical, claimed, claimOwner))
                        .defaultIfEmpty(new DirectDeliveryResult(record.publicId(), true, false))
                        .onErrorResume(error -> Mono.just(new DirectDeliveryResult(record.publicId(), true, false))))
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
                                                       NotificationRecord claimed, String claimOwner) {
        return notificationStore.canDeliver(notification).flatMap(allowed ->
                notificationStore.renewClaim(claimed.publicId(), claimOwner, Duration.ofSeconds(30)).flatMap(current -> {
            if (!current) return Mono.just(new DirectDeliveryResult(claimed.publicId(), true, false));
            if (!allowed) return notificationStore.suppressDelivery(claimed.publicId(), claimOwner, notification)
                    .map(suppressed -> new DirectDeliveryResult(claimed.publicId(), true, false, suppressed));
            return Mono.defer(() -> {
            boolean delivered = sseNotificationService.sendViaSSE(userId, claimed.publicId(), notification);
            if (delivered) {
                return notificationStore.recordAttempt(claimed.publicId(), "SSE", "WRITTEN", null, null)
                        .then(notificationStore.acknowledgeDelivery(claimed.publicId(), claimOwner))
                        .flatMap(acknowledged -> acknowledged
                                ? Mono.just(new DirectDeliveryResult(claimed.publicId(), true, true))
                                : scheduleDirectRetry(claimed, claimOwner, "ACKNOWLEDGEMENT_FAILED"));
            }
            return scheduleDirectRetry(claimed, claimOwner, "DELIVERY_UNAVAILABLE");
        });
        })).onErrorResume(error -> scheduleDirectRetry(claimed, claimOwner, "DELIVERY_ERROR"));
    }

    private Mono<DirectDeliveryResult> scheduleDirectRetry(NotificationRecord claimed, String claimOwner, String failureCode) {
        return notificationStore.renewClaim(claimed.publicId(), claimOwner, Duration.ofSeconds(30))
                .flatMap(current -> !current ? Mono.just(new DirectDeliveryResult(claimed.publicId(), true, false))
                        : notificationStore.recordAttempt(claimed.publicId(), "SSE", "FAILED", null, failureCode)
                .onErrorResume(error -> Mono.empty())
                .then(notificationStore.scheduleRetry(claimed.publicId(), claimOwner,
                        Instant.now().plusSeconds(30), failureCode, 3)
                        .onErrorResume(error -> Mono.just(false)))
                .thenReturn(new DirectDeliveryResult(claimed.publicId(), true, false)))
                .onErrorReturn(new DirectDeliveryResult(claimed.publicId(), true, false));
    }

    public Mono<Boolean> canDeliver(String recipientId, NotificationRequest notification) {
        return notificationStore.canDeliver(recipientId, notification);
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
