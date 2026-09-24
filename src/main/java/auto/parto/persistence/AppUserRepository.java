package auto.parto.persistence;

import reactor.core.publisher.Mono;

import java.util.UUID;

public interface AppUserRepository {

    Mono<AppUserRecord> findByPublicId(UUID publicId);

    Mono<AppUserRecord> findByEmail(String email);

    Mono<AppUserRecord> create(AppUserRecord user);
}
