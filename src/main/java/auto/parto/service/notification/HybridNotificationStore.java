package auto.parto.service.notification;

import auto.parto.dto.NotificationRequest;
import auto.parto.persistence.NotificationRepository;
import auto.parto.persistence.NotificationRecord;
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
    private final NotificationPreferenceFilter preferences;

    public HybridNotificationStore(RedisNotificationStore redisStore) {
        this.redisStore = redisStore;
        this.postgresqlRepository = null;
        this.objectMapper = null;
        this.preferences = null;
    }

    @Autowired
    public HybridNotificationStore(RedisNotificationStore redisStore,
                                   ObjectProvider<NotificationRepository> postgresqlRepositories,
                                   ObjectMapper objectMapper, NotificationPreferenceFilter preferences) {
        this.redisStore = redisStore;
        this.postgresqlRepository = postgresqlRepositories.getIfAvailable();
        this.objectMapper = objectMapper;
        this.preferences = preferences;
    }

    public Mono<Boolean> storePending(NotificationRequest notification) {
        return storePendingRecord(notification).map(record -> true).defaultIfEmpty(false);
    }

    /** Persists first and returns the authoritative public ID used by live delivery. */
    public Mono<NotificationRecord> storePendingRecord(NotificationRequest notification) {
        NotificationPolicy.category(notification.type() == null ? null : notification.type().name());
        String userId = notification.userId();
        if (postgresqlRepository == null) {
            return Mono.empty();
        }
        return parseUserId(userId)
                .flatMap(owner -> serialize(notification)
                        .flatMap(payload -> postgresqlRepository.enqueue(owner,
                                notification.type().name(), payload))
                        // PostgreSQL is authoritative; Redis is only a best-effort projection.
                        .flatMap(record -> redisStore.storePending(userId, notification)
                                .onErrorReturn(false)
                                .thenReturn(record)));
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
            NotificationRequest request = objectMapper.readValue(record.payloadJson(), NotificationRequest.class);
            NotificationPolicy.category(request.type() == null ? null : request.type().name());
            if (!record.recipientUserPublicId().toString().equals(request.userId())
                    || !record.eventType().equals(request.type().name())) return Mono.empty();
            return Mono.just(request);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
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
                                .map(notification -> new ClaimedNotification(record.publicId(), notification))
                                .switchIfEmpty(postgresqlRepository.suppressDelivery(record.publicId(), claimOwner,
                                        "INVALID_NOTIFICATION").then(Mono.empty()))));
    }

    public Flux<ClaimedNotification> replayNotifications(String userId, UUID afterEventId, int limit) {
        if (postgresqlRepository == null || afterEventId == null || limit < 1 || limit > 100) {
            return Flux.empty();
        }
        return parseUserId(userId)
                .flatMapMany(owner -> postgresqlRepository.findForRecipientAfter(owner, afterEventId, limit)
                        .flatMap(record -> deserialize(record)
                                .filterWhen(notification -> canDeliver(notification).onErrorReturn(false))
                                .map(notification -> new ClaimedNotification(record.publicId(), notification))))
                .onErrorResume(error -> {
                    log.warn("SSE notification replay is unavailable");
                    return Flux.empty();
                });
    }

    public Mono<Boolean> validateReplayCursor(String userId, UUID cursor) {
        if (postgresqlRepository == null || cursor == null) return Mono.just(false);
        return parseUserId(userId)
                .flatMap(owner -> postgresqlRepository.cursorBelongsToRecipient(owner, cursor))
                .onErrorReturn(false);
    }

    public Mono<NotificationRecord> claimNotification(UUID notificationPublicId, String claimOwner,
                                                       Instant now, Duration leaseDuration) {
        if (postgresqlRepository == null || notificationPublicId == null) return Mono.empty();
        return postgresqlRepository.claimNotification(notificationPublicId, claimOwner, now, leaseDuration);
    }

    public Mono<Void> rebuildProjection(String userId) {
        if (postgresqlRepository == null) return Mono.empty();
        return parseUserId(userId)
                .flatMap(owner -> redisStore.replacePendingNotifications(userId,
                        postgresqlRepository.findPendingForRecipient(owner, 100)
                                .flatMap(this::deserialize)))
                .then();
    }

    public Mono<Boolean> canDeliver(NotificationRequest notification) {
        return canDeliver(notification.userId(), notification);
    }

    public Mono<Boolean> canDeliver(String recipientId, NotificationRequest notification) {
        if (preferences == null) return Mono.error(new IllegalStateException("preferences unavailable"));
        return preferences.canSend(recipientId, "push", notification.type() == null ? null : notification.type().name());
    }

    public Mono<Boolean> suppressDelivery(UUID id, String claimOwner, NotificationRequest notification) {
        if (postgresqlRepository == null) return Mono.just(false);
        return postgresqlRepository.suppressDelivery(id, claimOwner, "PREFERENCE_DISABLED")
                .flatMap(suppressed -> suppressed
                        ? redisStore.removeNotification(notification.userId(), notification.id())
                            .onErrorReturn(0L).thenReturn(true)
                        : Mono.just(false));
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
