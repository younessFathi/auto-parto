package auto.parto.service.notification;

import auto.parto.dto.NotificationRequest;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class HybridNotificationStore {

    private final RedisNotificationStore redisStore;

    // Memory cache for hot data (recently accessed)
    private final Cache<String, List<NotificationRequest>> memoryCache;

    public HybridNotificationStore(RedisNotificationStore redisStore) {
        this.redisStore = redisStore;

        this.memoryCache = Caffeine.newBuilder()
                .maximumSize(1000) // Cache 1000 users
                .expireAfterWrite(10, TimeUnit.MINUTES) // 10 minute TTL
                .expireAfterAccess(5, TimeUnit.MINUTES) // 5 minutes since last access
                .build();
    }

    public Mono<Boolean> storePending(NotificationRequest notification) {
        String userId = notification.userId();
        // Clear memory cache for this user
        memoryCache.invalidate(userId);

        // Store in Redis (primary storage)
        return redisStore.storePending(userId, notification);
    }

    public Flux<NotificationRequest> getPendingNotifications(String userId) {
        // Check memory cache first
        List<NotificationRequest> cached = memoryCache.getIfPresent(userId);
        if (cached != null && !cached.isEmpty()) {
            log.debug("Cache hit for user {}: {} notifications", userId, cached.size());
            return Flux.fromIterable(cached);
        }

        // Cache miss - get from Redis
        log.info("Retreating pending notifications from redis ");
        return redisStore.getPendingNotifications(userId)
                .collectList()
                .flatMapMany(notifications -> {
                    // Store in cache
                    if (!notifications.isEmpty()) {
                        memoryCache.put(userId, notifications);
                    }
                    return Flux.fromIterable(notifications);
                });
    }

    public Mono<Long> clearPendingNotifications(String userId) {
        // Clear both cache and Redis
        memoryCache.invalidate(userId);
        return redisStore.clearPendingNotifications(userId);
    }

    public Mono<Map<String, ?>> getPendingStats(){
        return redisStore.getPendingStats();
    }

}