package auto.parto.service.notification;

import auto.parto.config.BaseIntegrationTest;
import auto.parto.dto.RegisterTokenRequest;
import auto.parto.dto.NotificationRequest;
import auto.parto.enums.NotificationType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.test.StepVerifier;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RedisNotificationStoreIT extends BaseIntegrationTest {

    @Autowired
    private RedisNotificationStore store;

    @Test
    void rebuildPreservesSameOwnerSamePlatformTokensForDeliveryLookup() {
        String userId = UUID.randomUUID().toString();
        String userKey = "fcm:user:tokens:" + userId;
        DeviceTokenService.DeviceToken first = token("redis-rebuild-token-1");
        DeviceTokenService.DeviceToken second = token("redis-rebuild-token-2");

        StepVerifier.create(store.registerRebuiltDeviceToken(first, userKey,
                                "fcm:token:owner:" + first.getToken(), userId, request(first.getToken()))
                        .then(store.registerRebuiltDeviceToken(second, userKey,
                                "fcm:token:owner:" + second.getToken(), userId, request(second.getToken())))
                        .then(store.getActiveTokens(userId, userKey)))
                .assertNext(tokens -> assertThat(tokens)
                        .containsExactlyInAnyOrder(first.getToken(), second.getToken()))
                .verifyComplete();
    }

    @Test
    void replacingPendingProjectionIsIdempotentAndBounded() {
        String userId = UUID.randomUUID().toString();
        NotificationRequest first = notification("notification-1", userId);
        NotificationRequest second = notification("notification-2", userId);

        StepVerifier.create(store.replacePendingNotifications(userId, Flux.just(first, second))
                        .then(store.replacePendingNotifications(userId, Flux.just(first, second)))
                        .thenMany(store.getPendingNotifications(userId).collectList()))
                .assertNext(notifications -> assertThat(notifications)
                        .extracting(NotificationRequest::id)
                        .containsExactly("notification-1", "notification-2"))
                .verifyComplete();
    }

    private static NotificationRequest notification(String id, String userId) {
        return new NotificationRequest(id, userId, NotificationType.NEW_SEARCH_POSTED, "safe", 1L);
    }

    private static DeviceTokenService.DeviceToken token(String value) {
        return DeviceTokenService.DeviceToken.builder()
                .token(value)
                .platform("android")
                .registeredAt(LocalDateTime.now())
                .lastUsed(LocalDateTime.now())
                .active(true)
                .build();
    }

    private static RegisterTokenRequest request(String token) {
        RegisterTokenRequest request = new RegisterTokenRequest();
        request.setDeviceToken(token);
        request.setPlatform("android");
        return request;
    }
}
