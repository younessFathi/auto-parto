package auto.parto.service.notification;

import auto.parto.dto.NotificationRequest;
import auto.parto.persistence.NotificationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;
import java.util.Collection;
import java.time.Duration;
import java.time.Instant;

@Component
@Slf4j
public class HybridNotificationStore {

    public record ClaimedNotification(UUID publicId, NotificationRequest notification) {
    }

    private final RedisNotificationStore redisStore;
    private final NotificationRepository postgresqlRepository;
    private final ObjectMapper objectMapper;

    public HybridNotificationStore(RedisNotificationStore redisStore) {
        this.redisStore = redisStore;
        this.postgresqlRepository = null;
        this.objectMapper = null;
    }

    @Autowired
    public HybridNotificationStore(RedisNotificationStore redisStore,
                                   ObjectProvider<NotificationRepository> postgresqlRepositories,
                                   ObjectMapper objectMapper) {
        this.redisStore = redisStore;
        this.postgresqlRepository = postgresqlRepositories.getIfAvailable();
        this.objectMapper = objectMapper;
    }

    public Mono<Boolean> storePending(NotificationRequest notification) {
        String userId = notification.userId();
        if (postgresqlRepository == null) {
            return Mono.just(false);
        }
        return parseUserId(userId)
                .flatMap(owner -> serialize(notification)
                        .flatMap(payload -> postgresqlRepository.enqueue(owner,
                                notification.type().name(), payload))
                        // PostgreSQL is authoritative; Redis is only a best-effort projection.
                        .flatMap(record -> redisStore.storePending(userId, notification)
                                .onErrorReturn(false)
                                .thenReturn(true)))
                .defaultIfEmpty(false);
    }

    public Flux<NotificationRequest> getPendingNotifications(String userId) {
        if (postgresqlRepository == null) return Flux.empty();
        return parseUserId(userId)
                .flatMapMany(owner -> postgresqlRepository.findPendingForRecipient(owner, 100)
                        .flatMap(this::deserialize))
                .onErrorResume(error -> Flux.empty());
    }

    public Mono<Long> clearPendingNotifications(String userId) {
        if (postgresqlRepository == null) return Mono.just(0L);
        return parseUserId(userId)
                .flatMap(postgresqlRepository::markPendingForRecipient)
                .defaultIfEmpty(0L)
                .flatMap(marked -> redisStore.clearPendingNotifications(userId).thenReturn(marked));
    }

    public Mono<Long> clearPendingNotifications(String userId, Collection<String> notificationIds) {
        if (notificationIds == null || notificationIds.isEmpty()) return Mono.just(0L);
        if (postgresqlRepository != null) {
            return parseUserId(userId)
                    .flatMapMany(owner -> Flux.fromIterable(notificationIds)
                            .flatMapSequential(id -> postgresqlRepository.markPendingForRecipientAndId(owner, id)))
                    .filter(Boolean::booleanValue)
                    .count()
                    .flatMap(marked -> removeRedisNotifications(userId, notificationIds).thenReturn(marked));
        }
        return Mono.just(0L);
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
        return Mono.just(Map.of("totalUsers", 0L, "totalNotifications", 0L));
    }

    public Flux<String> getPendingRecipientIds(UUID afterRecipientPublicId, int limit) {
        if (postgresqlRepository != null) {
            return postgresqlRepository.findPendingRecipientIds(afterRecipientPublicId, limit)
                    .map(UUID::toString);
        }
        return Flux.empty();
    }

    public Flux<ClaimedNotification> claimPendingNotifications(String userId, String claimOwner,
                                                                Instant now, Duration leaseDuration, int limit) {
        if (postgresqlRepository == null) return Flux.empty();
        return parseUserId(userId)
                .flatMapMany(owner -> postgresqlRepository
                        .claimPendingForRecipient(owner, claimOwner, now, leaseDuration, limit)
                        .flatMap(record -> deserialize(record)
                                .map(notification -> new ClaimedNotification(record.publicId(), notification))));
    }

    public Flux<ClaimedNotification> replayNotifications(String userId, UUID afterEventId, int limit) {
        if (postgresqlRepository == null || afterEventId == null || limit < 1 || limit > 100) {
            return Flux.empty();
        }
        return parseUserId(userId)
                .flatMapMany(owner -> postgresqlRepository.findForRecipientAfter(owner, afterEventId, limit)
                        .flatMap(record -> deserialize(record)
                                .map(notification -> new ClaimedNotification(record.publicId(), notification))))
                .onErrorResume(error -> {
                    log.warn("SSE notification replay is unavailable");
                    return Flux.empty();
                });
    }

    public Mono<Void> rebuildProjection(String userId) {
        if (postgresqlRepository == null) return Mono.empty();
        return parseUserId(userId)
                .flatMap(owner -> redisStore.replacePendingNotifications(userId,
                        postgresqlRepository.findPendingForRecipient(owner, 100)
                                .flatMap(this::deserialize)))
                .then();
    }

    public Mono<Boolean> acknowledgeDelivery(UUID notificationPublicId, String claimOwner) {
        if (postgresqlRepository == null || notificationPublicId == null) return Mono.just(false);
        return postgresqlRepository.acknowledgeDelivery(notificationPublicId, claimOwner);
    }

    public Mono<Boolean> scheduleRetry(UUID notificationPublicId, String claimOwner,
                                       Instant nextAttemptAt, String failureCode, int maxAttempts) {
        if (postgresqlRepository == null || notificationPublicId == null) return Mono.just(false);
        return postgresqlRepository.scheduleRetry(notificationPublicId, claimOwner,
                nextAttemptAt, failureCode, maxAttempts);
    }

    public Mono<Void> recordAttempt(UUID notificationPublicId, String channel, String status,
                                    String providerReference, String failureReason) {
        if (postgresqlRepository == null) return Mono.empty();
        return postgresqlRepository.recordAttempt(notificationPublicId, channel, status,
                providerReference, failureReason).then();
    }

}
