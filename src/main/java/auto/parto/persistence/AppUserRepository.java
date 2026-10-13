package auto.parto.persistence;

import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

import java.util.UUID;

public interface AppUserRepository {

    Mono<AppUserRecord> findByPublicId(UUID publicId);

    Mono<AppUserRecord> findByEmail(String email);

    Mono<AppUserRecord> create(AppUserRecord user);

    Flux<UUID> findPublicIdsByRole(String role, int limit);

    Mono<AppUserRecord> updateStatus(UUID publicId, String status);
}
