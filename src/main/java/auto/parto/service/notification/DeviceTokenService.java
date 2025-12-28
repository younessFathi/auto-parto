package auto.parto.service.notification;

import auto.parto.dto.RegisterTokenRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@Slf4j
@AllArgsConstructor
public class DeviceTokenService {

    private final Map<String, List<DeviceToken>> userTokens = new ConcurrentHashMap<>();
    private static final String USER_TOKENS_PREFIX = "fcm:user:tokens:";
    private static final String TOKEN_OWNER_PREFIX = "fcm:token:owner:";
    private final RedisNotificationStore redisNotificationStore;

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
            DeviceToken deviceToken = DeviceToken.builder()
                    .token(request.getToken())
                    .platform(request.getPlatform())
                    .registeredAt(LocalDateTime.now())
                    .lastUsed(LocalDateTime.now())
                    .active(true)
                    .appVersion(request.getAppVersion())
                    .build();

            String userKey = USER_TOKENS_PREFIX + userId;
            String ownerKey = TOKEN_OWNER_PREFIX + request.getToken();

        return redisNotificationStore.registerDeviceToken(deviceToken , userKey , ownerKey , userId , request);
    }

    /**
     * Get all active tokens for a user from Redis
     */
    public Mono<List<String>> getActiveTokens(String userId) {
        String userKey = USER_TOKENS_PREFIX + userId;
        return redisNotificationStore.getActiveTokens(userId, userKey);
    }

    /**
     * Mark token as invalid (when FCM returns invalid token error)
     */
    public void markTokenInvalid(String token) {
        userTokens.forEach((userId, tokens) -> {
            tokens.removeIf(t -> t.getToken().equals(token));
        });
        log.warn("Marked token as invalid: {}", maskToken(token));
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
}
