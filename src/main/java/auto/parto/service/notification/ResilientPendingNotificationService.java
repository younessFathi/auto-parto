package auto.parto.service.notification;

import auto.parto.config.DomainMetrics;
import auto.parto.config.ObservationSupport;
import auto.parto.dto.NotificationRequest;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import auto.parto.service.BasicCircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Component
@Slf4j
public class ResilientPendingNotificationService {

    private static final int RECOVERY_BATCH_SIZE = 100;
    private static final int MAX_ATTEMPTS = 3;
    private static final Duration CLAIM_LEASE = Duration.ofSeconds(30);
    private static final Duration RETRY_DELAY = Duration.ofSeconds(30);

    private final HybridNotificationStore notificationStore;
    private final SSENotificationService sseService;
    private final BasicCircuitBreaker circuitBreaker;
    private final ObservationRegistry observationRegistry;
    private final String workerId = "notification-worker-" + UUID.randomUUID();

    @Autowired
    public ResilientPendingNotificationService(HybridNotificationStore notificationStore,
                                                SSENotificationService sseService,
                                                BasicCircuitBreaker circuitBreaker,
                                                ObservationRegistry observationRegistry) {
        this.notificationStore = notificationStore;
        this.sseService = sseService;
        this.circuitBreaker = circuitBreaker;
        this.observationRegistry = observationRegistry;
    }

    public ResilientPendingNotificationService(HybridNotificationStore notificationStore,
                                                SSENotificationService sseService,
                                                BasicCircuitBreaker circuitBreaker) {
        this(notificationStore, sseService, circuitBreaker, ObservationRegistry.NOOP);
    }

    /** PostgreSQL enqueue is the only accepted durable write; Redis outage is not a fallback. */
    public Mono<Boolean> storeNotification(NotificationRequest notification) {
        return notificationStore.storePending(notification);
    }

    /** Claims a bounded batch and records each outcome in PostgreSQL before releasing the lease. */
    public Mono<Integer> deliverPendingOnConnect(String userId) {
        return claimAndDeliver(userId, false);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        recoverPending().subscribe(
                ignored -> { },
                error -> log.error("Notification recovery failed"),
                () -> log.info("Notification recovery completed"));
    }

    /** The scheduled lifecycle owns this subscription; no request path discards a reactive chain. */
    @Scheduled(fixedDelayString = "${notification.delivery.recovery-delay-ms:30000}")
    public void recoverDueNotifications() {
        recoverPending().subscribe(
                ignored -> { },
                error -> log.error("Scheduled notification recovery failed"));
    }

    private Mono<Integer> recoverPending() {
        return ObservationSupport.observe(observationRegistry, "notification.recovery",
                java.util.Map.of("job", "notification_recovery"), notificationStore.getPendingStats()
                .flatMapMany(stats -> {
                    Object total = stats.get("totalNotifications");
                    if (!(total instanceof Number number) || number.longValue() == 0) return Flux.empty();
                    return recoverRecipientPage(null);
                })
                .reduce(0, Integer::sum));
    }

    private Flux<Integer> recoverRecipientPage(UUID afterRecipientPublicId) {
        return notificationStore.getPendingRecipientIds(afterRecipientPublicId, RECOVERY_BATCH_SIZE)
                .collectList()
                .flatMapMany(recipients -> {
                    if (recipients.isEmpty()) return Flux.empty();
                    UUID nextCursor = UUID.fromString(recipients.getLast());
                    return Flux.fromIterable(recipients)
                            .flatMapSequential(this::claimAndDeliver, 1, 1)
                            .concatWith(recoverRecipientPage(nextCursor));
                });
    }

    private Mono<Integer> claimAndDeliver(String userId) {
        return claimAndDeliver(userId, true);
    }

    private Mono<Integer> claimAndDeliver(String userId, boolean requireActiveConnection) {
        if (requireActiveConnection && !sseService.hasActiveConnection(userId)) {
            return Mono.just(0);
        }
        return notificationStore.rebuildProjection(userId)
                .thenMany(notificationStore.claimPendingNotifications(userId, workerId, Instant.now(), CLAIM_LEASE,
                        RECOVERY_BATCH_SIZE))
                .flatMapSequential(this::deliverClaimed, 1, 1)
                .filter(Boolean::booleanValue)
                .count()
                .map(Long::intValue);
    }

    private Mono<Boolean> deliverClaimed(HybridNotificationStore.ClaimedNotification claimed) {
        NotificationRequest notification = claimed.notification();
        String userId = notification.userId();
        return ObservationSupport.observe(observationRegistry, "notification.delivery",
                java.util.Map.of("channel", "sse"), Mono.defer(() -> {
            boolean delivered = sseService.sendViaSSE(userId, claimed.publicId(), notification);
            if (delivered) {
                circuitBreaker.recordSuccess(userId);
                return notificationStore.recordAttempt(claimed.publicId(), "SSE", "WRITTEN", null, null)
                        .then(notificationStore.acknowledgeDelivery(claimed.publicId(), workerId))
                        .doOnSuccess(ignored -> DomainMetrics.notificationDelivery("sse", "acknowledged"));
            }
            circuitBreaker.recordFailure(userId);
            return notificationStore.recordAttempt(claimed.publicId(), "SSE", "FAILED", null,
                            "DELIVERY_UNAVAILABLE")
                    .then(notificationStore.scheduleRetry(claimed.publicId(), workerId,
                            Instant.now().plus(RETRY_DELAY), "DELIVERY_UNAVAILABLE", MAX_ATTEMPTS))
                    .doOnSuccess(ignored -> DomainMetrics.notificationDelivery("sse", "retry_scheduled"));
        }).onErrorResume(error -> {
            DomainMetrics.notificationDelivery("sse", "failure");
            return notificationStore.recordAttempt(claimed.publicId(), "SSE", "FAILED", null,
                        "DELIVERY_ERROR")
                .then(notificationStore.scheduleRetry(claimed.publicId(), workerId,
                        Instant.now().plus(RETRY_DELAY), "DELIVERY_ERROR", MAX_ATTEMPTS));
        }));
    }
}
