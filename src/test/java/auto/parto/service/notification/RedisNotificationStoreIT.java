package auto.parto.service.notification;

import auto.parto.config.BaseIntegrationTest;
import auto.parto.dto.RegisterTokenRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.test.StepVerifier;

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
