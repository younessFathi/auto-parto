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
public class DeviceTokenService {

    private final Map<String, List<DeviceToken>> userTokens = new ConcurrentHashMap<>();

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
        return Mono.fromCallable(() -> {
            DeviceToken deviceToken = DeviceToken.builder()
                    .token(request.getToken())
                    .platform(request.getPlatform())
                    .registeredAt(LocalDateTime.now())
                    .lastUsed(LocalDateTime.now())
                    .active(true)
                    .appVersion(request.getAppVersion())
                    .build();

            userTokens.compute(userId, (key, tokens) -> {
                if (tokens == null) {
                    tokens = new CopyOnWriteArrayList<>();
                }

                // Remove old tokens (same token or same device)
                tokens.removeIf(t ->
                        t.getToken().equals(request.getToken()) ||
                                isSameDevice(t, request)
                );

                tokens.add(deviceToken);

                // Keep only last 3 active tokens per user
                if (tokens.size() > 3) {
                    tokens.sort(Comparator.comparing(DeviceToken::getLastUsed).reversed());
                    tokens = new CopyOnWriteArrayList<>(tokens.subList(0, 3));
                }

                return tokens;
            });

            log.info("Registered token for user {}: {}", userId, maskToken(request.getToken()));
            return true;
        });
    }

    /**
     * Get all active tokens for a user
     */
    public List<String> getActiveTokens(String userId) {
        List<DeviceToken> tokens = userTokens.get(userId);
        if (tokens == null) {
            return List.of();
        }

        return tokens.stream()
                .filter(DeviceToken::isActive)
                .map(DeviceToken::getToken)
                .toList();
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
