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
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class DeviceTokenService {

    private final Map<String, List<DeviceToken>> userTokens = new ConcurrentHashMap<>();
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
                        .then(redisNotificationStore.registerDeviceToken(deviceToken, userKey, ownerKey, userId, request)))
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
        userTokens.forEach((userId, tokens) -> {
            tokens.removeIf(t -> t.getToken().equals(token));
        });
        log.warn("Marked token as invalid: {}", maskToken(token));
        Mono<Void> redisInvalidation = redisNotificationStore.markTokenInvalidByToken(token);
        if (postgresqlRepository != null) {
            redisInvalidation = Mono.when(redisInvalidation, postgresqlRepository.invalidate(token).then()).then();
        }
        return redisInvalidation;
    }

    public String maskToken(String token) {
        if (token == null || token.length() < 10) {
            return "***";
        }
        return token.substring(0, 6) + "..." + token.substring(token.length() - 4);
    }

    private boolean isSameDevice(DeviceToken existing, RegisterTokenRequest newRequest) {
        // Implement device identification logic
        return existing.getPlatform().equals(newRequest.getPlatform()) &&
                existing.getAppVersion().equals(newRequest.getAppVersion());
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
