package auto.parto.service.notification;

import auto.parto.persistence.DeviceTokenRecord;
import auto.parto.persistence.DeviceTokenRepository;
import auto.parto.dto.RegisterTokenRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeviceTokenServiceTest {

    private final RedisNotificationStore redis = mock(RedisNotificationStore.class);
    private final DeviceTokenRepository repository = mock(DeviceTokenRepository.class);
    private final ObjectProvider<DeviceTokenRepository> provider = mock(ObjectProvider.class);
    private final DeviceTokenService service;

    DeviceTokenServiceTest() {
        when(provider.getIfAvailable()).thenReturn(repository);
        service = new DeviceTokenService(redis, provider);
    }

    @Test
    void registrationRemainsSuccessfulWhenRedisProjectionIsUnavailable() {
        UUID owner = UUID.randomUUID();
        RegisterTokenRequest request = request("service-token-1", "android", owner.toString());
        DeviceTokenRecord record = record(owner, request.getDeviceToken(), "ANDROID");

        when(repository.register(owner, request.getDeviceToken(), request.getPlatform()))
                .thenReturn(Mono.just(record));
        when(redis.registerDeviceToken(any(), anyString(), anyString(), anyString(), any()))
                .thenReturn(Mono.error(new IllegalStateException("redis unavailable")));

        StepVerifier.create(service.registerToken(owner.toString(), request))
                .expectNext(true)
                .verifyComplete();

        verify(repository).register(owner, request.getDeviceToken(), request.getPlatform());
    }

    @Test
    void invalidationUpdatesPostgresqlBeforeRedisProjection() {
        String token = "service-token-2";
        when(repository.invalidate(token)).thenReturn(Mono.just(true));
        when(redis.markTokenInvalidByToken(token)).thenReturn(Mono.empty());

        StepVerifier.create(service.markTokenInvalid(token)).verifyComplete();

        var order = inOrder(repository, redis);
        order.verify(repository).invalidate(token);
        order.verify(redis).markTokenInvalidByToken(token);
    }

    @Test
    void rebuildsRedisProjectionAcrossBoundedPostgresqlPages() {
        UUID owner = UUID.randomUUID();
        DeviceTokenRecord first = record(owner, "service-token-3", "ANDROID");
        DeviceTokenRecord second = record(owner, "service-token-4", "IOS");
        when(repository.findActive(1)).thenReturn(Mono.just(first).flux());
        when(repository.findActiveAfter(first.lastSeenAt(), first.publicId(), 1))
                .thenReturn(Mono.just(second).flux());
        when(repository.findActiveAfter(second.lastSeenAt(), second.publicId(), 1))
                .thenReturn(reactor.core.publisher.Flux.empty());
        when(redis.registerDeviceToken(any(), anyString(), anyString(), anyString(), any()))
                .thenReturn(Mono.just(true));

        StepVerifier.create(service.rebuildRedisProjection(1))
                .expectNext(2)
                .verifyComplete();

        verify(redis, org.mockito.Mockito.times(2))
                .registerDeviceToken(any(), anyString(), anyString(), anyString(), any());
    }

    private static RegisterTokenRequest request(String token, String platform, String userId) {
        RegisterTokenRequest request = new RegisterTokenRequest();
        request.setDeviceToken(token);
        request.setPlatform(platform);
        request.setUserId(userId);
        return request;
    }

    private static DeviceTokenRecord record(UUID owner, String token, String platform) {
        Instant now = Instant.now();
        return new DeviceTokenRecord(UUID.randomUUID(), owner, token, platform, "ACTIVE", now, now, null);
    }
}
