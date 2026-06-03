package auto.parto.service.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
@RequiredArgsConstructor
public class PushNotificationQueue {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService retryExecutor;
    private final ResilientFcmService resilientFcmService;

    private static final String RETRY_QUEUE_PREFIX = "push:retry:";
    private static final String DEAD_LETTER_PREFIX = "push:dead:";

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class QueuedNotification {
        private String id;
        private String userId;
        private FcmNotification notification;
        private int retryCount;
        private long scheduledTime;
        private LocalDateTime queuedAt;
    }

    /**
     * Enqueue notification for retry
     */
    public Mono<Boolean> enqueueForRetry(String userId, FcmNotification notification) {
        QueuedNotification queued = QueuedNotification.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .notification(notification)
                .retryCount(0)
                .scheduledTime(System.currentTimeMillis() + 5000) // Retry in 5s
                .queuedAt(LocalDateTime.now())
                .build();

        return storeInQueue(queued)
                .doOnSuccess(success -> {
                    if (success) {
                        scheduleRetry(queued);
                        log.info("Notification queued for retry: {} for user {}",
                                queued.getId(), userId);
                    }
                });
    }

    /**
     * Store in Redis sorted set (by scheduledTime)
     */
    private Mono<Boolean> storeInQueue(QueuedNotification queued) {
        String key = RETRY_QUEUE_PREFIX + queued.getUserId();
        String value = serialize(queued);

        if (value == null) {
            return Mono.just(false);
        }

        return redisTemplate.opsForZSet()
                .add(key, value, queued.getScheduledTime());
    }

    /**
     * Schedule retry execution
     */
    private void scheduleRetry(QueuedNotification queued) {
        long delay = queued.getScheduledTime() - System.currentTimeMillis();

        if (delay > 0) {
            retryExecutor.schedule(() -> {
                processRetry(queued);
            }, delay, TimeUnit.MILLISECONDS);
        } else {
            processRetry(queued);
        }
    }

    /**
     * Process retry attempt
     */
    private void processRetry(QueuedNotification queued) {
        // Remove from queue first
        removeFromQueue(queued.getUserId(), queued.getId())
                .flatMap(removed -> {
                    if (!removed) {
                        log.warn("Notification {} not found in queue", queued.getId());
                        return Mono.just(false);
                    }

                    // Attempt redelivery
                    return attemptRedelivery(queued);
                })
                .subscribe();
    }

    /**
     * Attempt to redeliver the notification
     */
    Mono<Boolean> attemptRedelivery(QueuedNotification queued) {
        log.info("Attempting retry #{}/3 for notification {}",
                queued.getRetryCount() + 1, queued.getId());

        return resilientFcmService.sendToUser(queued.getUserId(), queued.getNotification())
                .map(FcmDeliveryResult::isSuccess)
                .flatMap(success -> {
                    if (success) {
                        log.info("Retry successful for notification {}", queued.getId());
                        return Mono.just(true);
                    } else {
                        return scheduleNextRetry(queued);
                    }
                });
    }

    /**
     * Schedule next retry with exponential backoff
     */
    private Mono<Boolean> scheduleNextRetry(QueuedNotification queued) {
        if (queued.getRetryCount() >= 3) { // Max 3 retries
            log.warn("Max retries reached for notification {}, moving to dead letter",
                    queued.getId());
            return moveToDeadLetter(queued);
        }

        // Calculate next delay: 5s, 30s, 5m
        long nextDelay = calculateBackoff(queued.getRetryCount() + 1);

        QueuedNotification nextRetry = QueuedNotification.builder()
                .id(queued.getId())
                .userId(queued.getUserId())
                .notification(queued.getNotification())
                .retryCount(queued.getRetryCount() + 1)
                .scheduledTime(System.currentTimeMillis() + nextDelay)
                .queuedAt(queued.getQueuedAt())
                .build();

        return storeInQueue(nextRetry)
                .doOnSuccess(success -> {
                    if (success) {
                        scheduleRetry(nextRetry);
                    }
                });
    }

    /**
     * Calculate exponential backoff
     */
    private long calculateBackoff(int retryCount) {
        return switch (retryCount) {
            case 1 -> 5000;    // 5 seconds
            case 2 -> 30000;   // 30 seconds
            case 3 -> 300000;  // 5 minutes
            default -> 5000;
        };
    }

    /**
     * Move to dead letter queue
     */
    private Mono<Boolean> moveToDeadLetter(QueuedNotification queued) {
        String key = DEAD_LETTER_PREFIX + queued.getUserId();
        String value = serialize(queued);

        if (value == null) {
            return Mono.just(false);
        }

        return redisTemplate.opsForList()
                .rightPush(key, value)
                .map(size -> {
                    log.error("Notification moved to dead letter: {} for user {}",
                            queued.getId(), queued.getUserId());
                    return size > 0;
                });
    }

    /**
     * Remove from retry queue
     */
    private Mono<Boolean> removeFromQueue(String userId, String notificationId) {
        String key = RETRY_QUEUE_PREFIX + userId;

        return redisTemplate.opsForZSet()
                .range(key, Range.unbounded())
                .collectList()
                .flatMap(members -> {
                    for (String member : members) {
                        QueuedNotification queued = deserialize(member);
                        if (queued != null && queued.getId().equals(notificationId)) {
                            return redisTemplate.opsForZSet()
                                    .remove(key, member)
                                    .map(count -> count > 0);
                        }
                    }
                    return Mono.just(false);
                });
    }

    // Helper methods
    private String serialize(QueuedNotification notification) {
        try {
            return objectMapper.writeValueAsString(notification);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize queued notification", e);
            return null;
        }
    }

    private QueuedNotification deserialize(String json) {
        try {
            return objectMapper.readValue(json, QueuedNotification.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize queued notification", e);
            return null;
        }
    }

    @PreDestroy
    public void shutdown() {
        retryExecutor.shutdown();
    }
}
