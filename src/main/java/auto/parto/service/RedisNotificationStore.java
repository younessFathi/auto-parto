package auto.parto.service;

import auto.parto.dto.NotificationRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class RedisNotificationStore {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${notification.redis.pending-prefix:notification:pending:}")
    private String pendingPrefix;

    @Value("${notification.redis.ttl-hours:24}")
    private int ttlHours;

    @Value("${notification.redis.max-pending-per-user:100}")
    private int maxPendingPerUser;

    // Initialize with JavaTimeModule for LocalDateTime support
    @PostConstruct
    public void init() {
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Store notification for later delivery
     */
    public Mono<Boolean> storePending(String userId, NotificationRequest notification) {
        String key = buildPendingKey(userId);
        String notificationJson = serializeNotification(notification);

        if (notificationJson == null) {
            return Mono.just(false);
        }

        return redisTemplate.opsForList()
                .rightPush(key, notificationJson)
                .flatMap(listSize -> {
                    // Enforce max size limit
                    if (listSize > maxPendingPerUser) {
                        return redisTemplate.opsForList()
                                .leftPop(key)
                                .thenReturn(listSize - 1);
                    }
                    return Mono.just(listSize);
                })
                .flatMap(listSize -> {
                    // Set TTL (refresh on each new addition)
                    return redisTemplate.expire(key, Duration.ofHours(ttlHours))
                            .thenReturn(true);
                })
                .doOnSuccess(success -> {
                    if (success) {
                        log.debug("Stored pending notification for user {} (Redis)", userId);
                    }
                })
                .onErrorResume(e -> {
                    log.error("Failed to store pending notification for {} in Redis: {}",
                            userId, e.getMessage());
                    return Mono.just(false);
                });
    }

    /**
     * Get all pending notifications for a user
     */
    public Flux<NotificationRequest> getPendingNotifications(String userId) {
        String key = buildPendingKey(userId);

        return redisTemplate.opsForList()
                .range(key, 0, -1) // Get all items
                .flatMapSequential(this::deserializeNotification)
                .collectList()
                .flatMapMany(notifications -> {
                    if (!notifications.isEmpty()) {
                        log.info("Retrieved {} pending notifications from Redis for {}",
                                notifications.size(), userId);
                    }
                    return Flux.fromIterable(notifications);
                })
                .onErrorResume(e -> {
                    log.error("Failed to retrieve pending notifications for {}: {}",
                            userId, e.getMessage());
                    return Flux.empty();
                });
    }

    /**
     * Remove all pending notifications after delivery
     */
    public Mono<Long> clearPendingNotifications(String userId) {
        String key = buildPendingKey(userId);

        return redisTemplate.delete(key)
                .doOnSuccess(count -> {
                    if (count > 0) {
                        log.info("Cleared {} pending notifications from Redis for {}",
                                count, userId);
                    }
                })
                .onErrorResume(e -> {
                    log.error("Failed to clear pending notifications for {}: {}",
                            userId, e.getMessage());
                    return Mono.just(0L);
                });
    }

    /**
     * Get count of pending notifications
     */
    public Mono<Long> getPendingCount(String userId) {
        String key = buildPendingKey(userId);

        return redisTemplate.opsForList()
                .size(key)
                .defaultIfEmpty(0L)
                .onErrorResume(e -> {
                    log.error("Failed to get pending count for {}: {}", userId, e.getMessage());
                    return Mono.just(0L);
                });
    }

    /**
     * Remove specific notification from pending (e.g., if expired)
     */
    public Mono<Long> removeNotification(String userId, String notificationId) {
        String key = buildPendingKey(userId);

        return redisTemplate.opsForList()
                .range(key, 0, -1)
                .collectList()
                .flatMap(items -> Flux.fromIterable(items)
                        .flatMap(json -> deserializeNotification(json).map(notif -> Map.entry(json, notif)))
                        .filter(entry -> entry.getValue() != null && entry.getValue().id().equals(notificationId))
                        .next()
                        .flatMap(entry -> redisTemplate.opsForList().remove(key, 1, entry.getKey()))
                        .defaultIfEmpty(0L)
                );
    }

    /**
     * Get statistics about pending notifications
     */
    public Mono<Map<String, ?>> getPendingStats() {
        String pattern = pendingPrefix + "*";

        return redisTemplate.keys(pattern)
                .collectList()
                .flatMap(keys -> {
                    if (keys.isEmpty()) {
                        return Mono.just(Map.of(
                                "totalUsers", 0L,
                                "totalNotifications", 0L,
                                "timestamp", LocalDateTime.now()
                        ));
                    }

                    // Get counts for all users
                    Flux<Long> counts = Flux.fromIterable(keys)
                            .flatMap(key -> redisTemplate.opsForList().size(key));

                    return counts.reduce(0L, Long::sum)
                            .map(totalNotifications -> Map.<String, Object>of(
                                    "totalUsers", (long) keys.size(),
                                    "totalNotifications", totalNotifications,
                                    "usersWithPending", keys.stream()
                                            .map(k -> k.substring(pendingPrefix.length()))
                                            .toList(),
                                    "timestamp", LocalDateTime.now()
                            ));
                })
                .onErrorResume(e -> {
                    log.error("Failed to get pending stats: {}", e.getMessage());
                    return Mono.just(Map.of("error", e.getMessage()));
                });
    }

    /**
     * Clean up expired notifications (scheduled task)
     */
    @Scheduled(cron = "0 0 3 * * *") // Daily at 3 AM
    public Mono<Void> cleanupExpiredNotifications() {
        String pattern = pendingPrefix + "*";

        return redisTemplate.keys(pattern)
                .flatMap(key -> {
                    // Check TTL for each key
                    return redisTemplate.getExpire(key)
                            .filter(ttl -> ttl != null && ttl.getSeconds() <= 0)
                            .flatMap(ttl -> {
                                log.info("Cleaning up expired notifications for key: {}", key);
                                return redisTemplate.delete(key).then();
                            });
                })
                .then()
                .doOnSuccess(v -> log.info("Expired notifications cleanup completed"))
                .doOnError(e -> log.error("Cleanup failed: {}", e.getMessage()));
    }

    // Helper methods

    private String buildPendingKey(String userId) {
        return pendingPrefix + userId;
    }

    private String serializeNotification(NotificationRequest notification) {
        try {
            return objectMapper.writeValueAsString(notification);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize notification: {}", e.getMessage());
            return null;
        }
    }

    private Mono<NotificationRequest> deserializeNotification(String json) {
        return Mono.fromCallable(() -> objectMapper.readValue(json, NotificationRequest.class))
                .onErrorResume(e -> {
                    log.error("Failed to deserialize notification JSON: {}", e.getMessage());
                    return Mono.empty();
                });
    }
}
