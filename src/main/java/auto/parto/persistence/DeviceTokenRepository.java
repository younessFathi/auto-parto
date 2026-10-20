package auto.parto.persistence;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

public interface DeviceTokenRepository {

    Mono<DeviceTokenRecord> register(UUID ownerUserPublicId, String providerToken, String platform);

    Flux<DeviceTokenRecord> findActiveByOwnerPublicId(UUID ownerUserPublicId, int limit);

    Flux<DeviceTokenRecord> findActive(int limit);

    Flux<DeviceTokenRecord> findActiveAfter(Instant lastSeenAt, UUID publicId, int limit);

    Mono<Boolean> invalidate(String providerToken);
}
