package auto.parto.persistence;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface DeviceTokenRepository {

    Mono<DeviceTokenRecord> register(UUID ownerUserPublicId, String providerToken, String platform);

    Flux<DeviceTokenRecord> findActiveByOwnerPublicId(UUID ownerUserPublicId, int limit);

    Mono<Boolean> invalidate(String providerToken);
}
