package auto.parto.service.notification;

import auto.parto.dto.NotificationRequest;
import auto.parto.service.BasicCircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class ResilientPendingNotificationService {

    private final HybridNotificationStore notificationStore;
    private final SSENotificationService sseService;
    private final BasicCircuitBreaker circuitBreaker;

    // Memory queue for recently failed deliveries (circuit breaker open)
    private final Map<String, Queue<NotificationRequest>> memoryFallback = new ConcurrentHashMap<>();

    /**
     * Store notification with resilience strategy
     */
    public Mono<Boolean> storeNotification(NotificationRequest notification) {
        String userId = notification.userId();
        // 1. Try Redis storage (primary)
        return notificationStore.storePending(notification)
                .flatMap(redisSuccess -> {
                    if (redisSuccess) {
                        return Mono.just(true);
                    }

                    // 2. Redis failed, use memory fallback
                    log.warn("Redis storage failed for user {}, using memory fallback", userId);
                    return storeInMemoryFallback(userId, notification)
                            .map(memorySuccess -> {
                                if (!memorySuccess) {
                                    log.error("ALL storage failed for notification: {}", notification.id());
                                }
                                return memorySuccess;
                            });
                });
    }

    /**
     * Deliver pending notifications when user connects
     */
    public Mono<Integer> deliverPendingOnConnect(String userId) {
        return notificationStore.getPendingNotifications(userId)
                .flatMapSequential(notification -> attemptDeliveryWithRetry(userId, notification))
                .filter(Boolean::booleanValue)
                .collectList()
                .zipWhen(successfulDeliveries ->
                        successfulDeliveries.isEmpty() ?
                                Mono.just(0L) :
                                notificationStore.clearPendingNotifications(userId)
                )
                .map(tuple -> {
                    List<Boolean> successfulDeliveries = tuple.getT1();
                    return successfulDeliveries.size();
                });
    }
    /**
     * Emergency recovery: Load all pending notifications on server restart
     */
    @EventListener(ApplicationReadyEvent.class)
    public Mono<Object> recoverOnStartup() {
        log.info("Starting notification recovery on server startup...");

        return notificationStore.getPendingStats()
                .flatMap(stats -> {
                    Long totalNotifications = (Long) stats.get("totalNotifications");
                    Long totalUsers = (Long) stats.get("totalUsers");

                    log.info("Found {} pending notifications for {} users",
                            totalNotifications, totalUsers);

                    if (totalNotifications == null || totalNotifications == 0) {
                        return Mono.empty();
                    }

                    // Log users with pending notifications
                    @SuppressWarnings("unchecked")
                    List<String> users = (List<String>) stats.get("usersWithPending");
                    users.forEach(user ->
                            log.info("User {} has pending notifications", user)
                    );

                    return Mono.empty();
                })
                .doOnSuccess(v -> log.info("Notification recovery completed"))
                .doOnError(e -> log.error("Recovery failed: {}", e.getMessage()));
    }

    // Private helper methods

    private Mono<Boolean> storeInMemoryFallback(String userId, NotificationRequest notification) {
        return Mono.fromCallable(() -> {
            memoryFallback.compute(userId, (key, queue) -> {
                if (queue == null) {
                    queue = new LinkedList<>();
                }

                // Limit memory usage
                if (queue.size() >= 50) {
                    queue.poll();
                    log.warn("Memory fallback queue full for {}, removed oldest", userId);
                }

                queue.offer(notification);
                return queue;
            });

            // Schedule sync to Redis (when it's back up)
            scheduleRedisSync(userId);

            return true;
        });
    }

    private void scheduleRedisSync(String userId) {
        Mono.delay(Duration.ofMinutes(1))
                .flatMap(tick -> {
                    Queue<NotificationRequest> queue = memoryFallback.get(userId);
                    if (queue == null || queue.isEmpty()) {
                        return Mono.empty();
                    }

                    // Try to move memory items to Redis
                    return Flux.fromIterable(queue)
                            .flatMap(notificationStore::storePending)
                            .collectList()
                            .doOnSuccess(results -> {
                                long successCount = results.stream().filter(Boolean::booleanValue).count();
                                if (successCount > 0) {
                                    log.info("Synced {} notifications from memory to Redis for {}",
                                            successCount, userId);
                                    // Clear successfully synced items
                                    memoryFallback.remove(userId);
                                }
                            });
                })
                .subscribe();
    }

    private Mono<Boolean> attemptDeliveryWithRetry(String userId, NotificationRequest notification) {
        log.info("Delivering pending notifications");
        return Mono.defer(() -> {
            boolean delivered = sseService.sendViaSSE(userId, notification);
            if (delivered) {
                circuitBreaker.recordSuccess(userId);
                return Mono.just(true);
            } else {
                circuitBreaker.recordFailure(userId);

                // Retry once after delay
                return Mono.delay(Duration.ofSeconds(2))
                        .flatMap(tick -> {
                            boolean retryDelivered = sseService.sendViaSSE(userId, notification);
                            if (retryDelivered) {
                                circuitBreaker.recordSuccess(userId);
                            }
                            return Mono.just(retryDelivered);
                        })
                        .defaultIfEmpty(false);
            }
        });
    }
}