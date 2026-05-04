package auto.parto.repository;

import auto.parto.model.NotificationPreferences;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface NotificationPreferencesRepository extends ReactiveMongoRepository<NotificationPreferences, String> {
    Mono<NotificationPreferences> findByUserId(String userId);
}