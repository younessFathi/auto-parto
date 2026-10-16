package auto.parto.service.notification;

import auto.parto.dto.NotificationRequest;
import auto.parto.persistence.NotificationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.UUID;
import java.util.Collection;

@Component
@Slf4j
public class HybridNotificationStore {

    private final RedisNotificationStore redisStore;
    private final NotificationRepository postgresqlRepository;
    private final ObjectMapper objectMapper;

    // Memory cache for hot data (recently accessed)
    private final Cache<String, List<NotificationRequest>> memoryCache;

    public HybridNotificationStore(RedisNotificationStore redisStore) {
        this.redisStore = redisStore;
        this.postgresqlRepository = null;
        this.objectMapper = null;
        this.memoryCache = createMemoryCache();
    }

    @Autowired
    public HybridNotificationStore(RedisNotificationStore redisStore,
                                   ObjectProvider<NotificationRepository> postgresqlRepositories,
                                   ObjectMapper objectMapper) {
        this.redisStore = redisStore;
        this.postgresqlRepository = postgresqlRepositories.getIfAvailable();
        this.objectMapper = objectMapper;

        this.memoryCache = createMemoryCache();

    }

    private Cache<String, List<NotificationRequest>> createMemoryCache() {
        return Caffeine.newBuilder()
                .maximumSize(1000) // Cache 1000 users
                .expireAfterWrite(10, TimeUnit.MINUTES) // 10 minute TTL
                .expireAfterAccess(5, TimeUnit.MINUTES) // 5 minutes since last access
                .build();
    }

    public Mono<Boolean> storePending(NotificationRequest notification) {
        String userId = notification.userId();
        // Clear memory cache for this user
        memoryCache.invalidate(userId);

        if (postgresqlRepository == null) {
            return redisStore.storePending(userId, notification);
        }
        return parseUserId(userId)
                .flatMap(owner -> serialize(notification)
                        .flatMap(payload -> postgresqlRepository.enqueue(owner,
                                notification.type().name(), payload))
                        .flatMap(record -> redisStore.storePending(userId, notification)))
                .defaultIfEmpty(false);
    }

    public Flux<NotificationRequest> getPendingNotifications(String userId) {
        // Check memory cache first
        List<NotificationRequest> cached = memoryCache.getIfPresent(userId);
        if (cached != null && !cached.isEmpty()) {
            log.debug("Cache hit for user {}: {} notifications", userId, cached.size());
            return Flux.fromIterable(cached);
        }

        if (postgresqlRepository != null) {
            return parseUserId(userId)
                    .flatMapMany(owner -> postgresqlRepository.findPendingForRecipient(owner, 100)
                            .flatMap(this::deserialize)
                            .collectList()
                            .doOnNext(notifications -> {
                                if (!notifications.isEmpty()) memoryCache.put(userId, notifications);
                            })
                            .flatMapMany(Flux::fromIterable))
                    .onErrorResume(error -> {
                        log.error("Failed to retrieve pending notifications from PostgreSQL for {}: {}",
                                userId, error.getMessage());
                        return Flux.empty();
                    });
        }

        // Cache miss - get from Redis
        log.info("Retrieving pending notifications from Redis");
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
        if (postgresqlRepository != null) {
            return parseUserId(userId)
                    .flatMap(postgresqlRepository::markPendingForRecipient)
                    .defaultIfEmpty(0L)
                    .flatMap(marked -> redisStore.clearPendingNotifications(userId).thenReturn(marked));
        }
        return redisStore.clearPendingNotifications(userId);
    }

    public Mono<Long> clearPendingNotifications(String userId, Collection<String> notificationIds) {
        memoryCache.invalidate(userId);
        if (notificationIds == null || notificationIds.isEmpty()) return Mono.just(0L);
        if (postgresqlRepository != null) {
            return parseUserId(userId)
                    .flatMapMany(owner -> Flux.fromIterable(notificationIds)
                            .flatMapSequential(id -> postgresqlRepository.markPendingForRecipientAndId(owner, id)))
                    .filter(Boolean::booleanValue)
                    .count()
                    .flatMap(marked -> removeRedisNotifications(userId, notificationIds).thenReturn(marked));
        }
        return removeRedisNotifications(userId, notificationIds);
    }

    private Mono<Long> removeRedisNotifications(String userId, Collection<String> notificationIds) {
        return Flux.fromIterable(notificationIds)
                .flatMapSequential(id -> redisStore.removeNotification(userId, id))
                .reduce(0L, Long::sum);
    }

    private Mono<UUID> parseUserId(String userId) {
        try { return Mono.just(UUID.fromString(userId)); }
        catch (IllegalArgumentException exception) { return Mono.empty(); }
    }

    private Mono<NotificationRequest> deserialize(auto.parto.persistence.NotificationRecord record) {
        if (objectMapper == null) return Mono.empty();
        try {
            return Mono.just(objectMapper.readValue(record.payloadJson(), NotificationRequest.class));
        } catch (JsonProcessingException exception) {
            log.warn("Skipping malformed PostgreSQL notification {}", record.publicId());
            return Mono.empty();
        }
    }

    private Mono<String> serialize(NotificationRequest notification) {
        try { return Mono.just(objectMapper.writeValueAsString(notification)); }
        catch (JsonProcessingException exception) { return Mono.empty(); }
    }

    public Mono<Map<String, ?>> getPendingStats(){
        if (postgresqlRepository != null) {
            return Mono.zip(postgresqlRepository.countPending(),
                            postgresqlRepository.findPendingRecipientIds(100).map(UUID::toString).collectList())
                    .map(result -> Map.of(
                            "totalUsers", (long) result.getT2().size(),
                            "totalNotifications", result.getT1(),
                            "usersWithPending", result.getT2()));
        }
        return redisStore.getPendingStats();
    }

    public Flux<String> getPendingRecipientIds(UUID afterRecipientPublicId, int limit) {
        if (postgresqlRepository != null) {
            return postgresqlRepository.findPendingRecipientIds(afterRecipientPublicId, limit)
                    .map(UUID::toString);
        }
        return redisStore.getPendingStats()
                .flatMapMany(stats -> {
                    Object value = stats.get("usersWithPending");
                    if (!(value instanceof Collection<?> users)) {
                        return Flux.empty();
                    }
                    return Flux.fromIterable(users)
                            .map(Object::toString)
                            .flatMap(this::parseUserId)
                            .filter(id -> afterRecipientPublicId == null
                                    || id.compareTo(afterRecipientPublicId) > 0)
                            .sort()
                            .take(limit)
                            .map(UUID::toString);
                });
    }

}
