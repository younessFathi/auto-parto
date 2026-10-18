package auto.parto.config;

import auto.parto.audit.AuditProcessingProperties;
import auto.parto.audit.AuditPublisher;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** Keeps application readiness down until the configured audit database responds. */
@Component("auditReadiness")
@ConditionalOnProperty(prefix = "auto.parto.audit", name = "enabled", havingValue = "true")
public final class AuditReadinessHealthIndicator implements ReactiveHealthIndicator {

    private final AuditPublisher publisher;
    private final AuditProcessingProperties properties;

    public AuditReadinessHealthIndicator(AuditPublisher publisher, AuditProcessingProperties properties) {
        this.publisher = publisher;
        this.properties = properties;
    }

    @Override
    public Mono<Health> health() {
        return publisher.readiness()
                .timeout(properties.getReadinessTimeout())
                .thenReturn(Health.up().withDetail("auditDatabase", "CONNECTED").build())
                .onErrorResume(error -> Mono.just(
                        Health.down().withDetail("auditDatabase", "UNAVAILABLE").build()));
    }
}
