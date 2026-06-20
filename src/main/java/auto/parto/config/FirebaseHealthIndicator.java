package auto.parto.config;

import com.google.firebase.FirebaseApp;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class FirebaseHealthIndicator implements ReactiveHealthIndicator {

    @Override
    public Mono<Health> health() {
        return Mono.fromCallable(() -> {
            try {
                FirebaseApp.getInstance();
                return Health.up().withDetail("firebase", "CONNECTED").build();
            } catch (Exception e) {
                return Health.down().withDetail("firebase", "DISCONNECTED")
                        .withDetail("error", e.getMessage()).build();
            }
        });
    }
}
