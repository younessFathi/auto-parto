package auto.parto.config;

import com.google.firebase.FirebaseApp;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@ConditionalOnProperty(name = "app.firebase.components-enabled", havingValue = "true", matchIfMissing = true)
public class FirebaseHealthIndicator implements ReactiveHealthIndicator {

    @Override
    public Mono<Health> health() {
        return Mono.fromCallable(() -> {
            try {
                FirebaseApp.getInstance();
                return Health.up().withDetail("firebase", "CONNECTED").build();
            } catch (Exception ignored) {
                return Health.down().withDetail("firebase", "DISCONNECTED").build();
            }
        });
    }
}
