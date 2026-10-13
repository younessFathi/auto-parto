package auto.parto.repository;

import auto.parto.model.NotificationPreferences;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import reactor.core.publisher.Mono;

@Repository
@ConditionalOnProperty(prefix = "auto.parto.persistence.postgresql.cutover", name = "mongo-disabled",
        havingValue = "false", matchIfMissing = true)
public interface NotificationPreferencesRepository extends ReactiveMongoRepository<NotificationPreferences, String> {
    Mono<NotificationPreferences> findByUserId(String userId);
}
