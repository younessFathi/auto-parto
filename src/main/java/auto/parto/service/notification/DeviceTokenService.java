package auto.parto.service.notification;

import auto.parto.dto.RegisterTokenRequest;
import auto.parto.persistence.DeviceTokenRepository;
import auto.parto.persistence.DeviceTokenRecord;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class DeviceTokenService {

    private static final String USER_TOKENS_PREFIX = "fcm:user:tokens:";
    private static final String TOKEN_OWNER_PREFIX = "fcm:token:owner:";
    private final RedisNotificationStore redisNotificationStore;
    private final DeviceTokenRepository postgresqlRepository;

    public DeviceTokenService(RedisNotificationStore redisNotificationStore,
                              ObjectProvider<DeviceTokenRepository> postgresqlRepositories) {
        this.redisNotificationStore = redisNotificationStore;
        this.postgresqlRepository = postgresqlRepositories.getIfAvailable();
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DeviceToken {
        private String token;
        private String platform; // "android", "ios", "web"
        private LocalDateTime registeredAt;
        private LocalDateTime lastUsed;
        private boolean active;
        private String appVersion;
    }

    /**
     * Register a new device token
     */
    public Mono<Boolean> registerToken(String userId, RegisterTokenRequest request) {
        DeviceToken deviceToken = buildDeviceToken(request);
        String userKey = USER_TOKENS_PREFIX + userId;
        String ownerKey = TOKEN_OWNER_PREFIX + request.getDeviceToken();
        if (postgresqlRepository == null) {
            return redisNotificationStore.registerDeviceToken(deviceToken, userKey, ownerKey, userId, request);
        }
        return postgresqlOwner(userId)
                .flatMap(owner -> postgresqlRepository.register(owner, request.getDeviceToken(), request.getPlatform())
                        .flatMap(token -> projectToRedis(deviceToken, userKey, ownerKey, userId, request)
                                .onErrorResume(error -> {
                                    log.warn("Device token projection could not be updated; PostgreSQL remains authoritative",
                                            error);
                                    return Mono.just(true);
                                })))
                .defaultIfEmpty(false);
    }

    private static DeviceToken buildDeviceToken(RegisterTokenRequest request) {
       return DeviceToken.builder()
                .token(request.getDeviceToken())
                .platform(request.getPlatform())
                .registeredAt(LocalDateTime.now())
                .lastUsed(LocalDateTime.now())
                .active(true)
                .appVersion(request.getAppVersion())
                .build();
    }

    /**
     * Get all active tokens for a user from Redis
     */
    public Mono<List<String>> getActiveTokens(String userId) {
        String userKey = USER_TOKENS_PREFIX + userId;
        if (postgresqlRepository == null) {
            return redisNotificationStore.getActiveTokens(userId, userKey);
        }
        return postgresqlOwner(userId)
                .flatMapMany(owner -> postgresqlRepository.findActiveByOwnerPublicId(owner, 3)
                        .map(DeviceTokenRecord::providerToken)
                        .collectList())
                .next()
                .defaultIfEmpty(List.of());
    }

    /**
     * Mark token as invalid (when FCM returns invalid token error)
     */
    public Mono<Void> markTokenInvalid(String token) {
        log.warn("Marked token as invalid: {}", maskToken(token));
        Mono<Void> redisInvalidation = Mono.defer(() -> redisNotificationStore.markTokenInvalidByToken(token));
        if (postgresqlRepository == null) {
            return redisInvalidation;
        }
        return postgresqlRepository.invalidate(token)
                .then(redisInvalidation.onErrorResume(error -> {
                    log.warn("Device token Redis projection could not be invalidated; PostgreSQL is authoritative",
                            error);
                    return Mono.empty();
                }));
    }

    /**
     * Rebuilds the complete Redis device-token projection in sequential pages.
     * The limit bounds each query and in-memory page; the operation continues until
     * all active registrations have been processed.
     */
    public Mono<Integer> rebuildRedisProjection(int limit) {
        if (postgresqlRepository == null) {
            return Mono.just(0);
        }
        if (limit < 1 || limit > 1000) {
            return Mono.error(new IllegalArgumentException("limit must be between 1 and 1000"));
        }
        return rebuildRedisProjectionPage(null, null, limit)
                .reduce(0, Integer::sum);
    }

    private Flux<Integer> rebuildRedisProjectionPage(java.time.Instant lastSeenAt, java.util.UUID publicId,
                                                     int limit) {
        Mono<java.util.List<DeviceTokenRecord>> page = lastSeenAt == null
                ? postgresqlRepository.findActive(limit).collectList()
                : postgresqlRepository.findActiveAfter(lastSeenAt, publicId, limit).collectList();
        return page.flatMapMany(records -> {
            if (records.isEmpty()) {
                return Flux.empty();
            }
            Flux<Integer> projected = Flux.fromIterable(records)
                    .concatMap(record -> {
                    DeviceToken deviceToken = DeviceToken.builder()
                            .token(record.providerToken())
                            .platform(record.platform().toLowerCase(java.util.Locale.ROOT))
                            .registeredAt(record.createdAt().atZone(java.time.ZoneOffset.UTC).toLocalDateTime())
                            .lastUsed(record.lastSeenAt().atZone(java.time.ZoneOffset.UTC).toLocalDateTime())
                            .active(true)
                            .build();
                    String userId = record.ownerUserPublicId().toString();
                    return projectRebuiltTokenToRedis(deviceToken, USER_TOKENS_PREFIX + userId,
                            TOKEN_OWNER_PREFIX + record.providerToken(), userId,
                            registrationRequest(record)).thenReturn(1);
                    });
            if (records.size() < limit) {
                return projected;
            }
            DeviceTokenRecord last = records.get(records.size() - 1);
            return projected.concatWith(rebuildRedisProjectionPage(last.lastSeenAt(), last.publicId(), limit));
        });
    }

    private Mono<Boolean> projectToRedis(DeviceToken deviceToken, String userKey, String ownerKey,
                                         String userId, RegisterTokenRequest request) {
        return redisNotificationStore.registerDeviceToken(deviceToken, userKey, ownerKey, userId, request);
    }

    private Mono<Boolean> projectRebuiltTokenToRedis(DeviceToken deviceToken, String userKey, String ownerKey,
                                                     String userId, RegisterTokenRequest request) {
        return redisNotificationStore.registerRebuiltDeviceToken(deviceToken, userKey, ownerKey, userId, request);
    }

    private static RegisterTokenRequest registrationRequest(DeviceTokenRecord record) {
        RegisterTokenRequest request = new RegisterTokenRequest();
        request.setDeviceToken(record.providerToken());
        request.setPlatform(record.platform());
        request.setUserId(record.ownerUserPublicId().toString());
        return request;
    }

    public String maskToken(String token) {
        if (token == null || token.length() < 10) {
            return "***";
        }
        return token.substring(0, 6) + "..." + token.substring(token.length() - 4);
    }

    private Mono<java.util.UUID> postgresqlOwner(String userId) {
        if (postgresqlRepository == null) {
            return Mono.empty();
        }
        try {
            return Mono.just(java.util.UUID.fromString(userId));
        } catch (IllegalArgumentException exception) {
            return Mono.empty();
        }
    }
}
