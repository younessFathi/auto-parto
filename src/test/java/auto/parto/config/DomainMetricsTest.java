package auto.parto.config;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

class DomainMetricsTest {
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @BeforeEach
    void registerTestRegistry() {
        Metrics.addRegistry(registry);
    }

    @AfterEach
    void removeTestRegistry() {
        Metrics.removeRegistry(registry);
        registry.close();
    }

    @Test
    void recordsWalletMetricsWithOnlyBoundedTags() {
        String privateIdentifier = "user-3a910b";

        DomainMetrics.observe(privateIdentifier, privateIdentifier, Mono.just("ok")).block();
        DomainMetrics.walletMutation("credit", "success");

        var timer = Metrics.globalRegistry.find("auto.parto.domain.operation")
                .tag("domain", "other").tag("operation", "other").tag("outcome", "success").timer();
        var counter = Metrics.globalRegistry.find("auto.parto.wallet.mutations")
                .tag("operation", "credit").tag("outcome", "success").counter();
        assertThat(timer).isNotNull();
        assertThat(counter).isNotNull();
        assertThat(Metrics.globalRegistry.getMeters()).noneMatch(meter -> meter.getId().getTags().stream()
                .anyMatch(tag -> tag.getValue().contains(privateIdentifier)));
    }
}
