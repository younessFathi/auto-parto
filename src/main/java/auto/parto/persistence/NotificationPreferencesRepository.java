package auto.parto.persistence;

import reactor.core.publisher.Mono;

import java.util.UUID;

public interface NotificationPreferencesRepository {

    Mono<NotificationPreferencesRecord> findByUserPublicId(UUID userPublicId);

    Mono<NotificationPreferencesRecord> save(UUID userPublicId, NotificationPreferencesRecord preferences);
}
