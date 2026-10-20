package auto.parto.config;

import auto.parto.service.StorageReconciliationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Keeps readiness unhealthy until asynchronous storage reconciliation completes. */
@Component
@Slf4j
@ConditionalOnProperty(prefix = "auto.parto.persistence.postgresql.cutover.storage-reconciliation",
        name = "enabled", havingValue = "true")
public final class StorageReconciliationStartupGate
        implements ApplicationListener<ApplicationStartedEvent>, ReactiveHealthIndicator {

    private final StorageReconciliationService reconciliation;
    private final Duration timeout;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicReference<Health> state = new AtomicReference<>(
            Health.down().withDetail("reason", "storage reconciliation pending").build());

    public StorageReconciliationStartupGate(StorageReconciliationService reconciliation, Duration timeout) {
        this.reconciliation = reconciliation;
        this.timeout = timeout;
    }

    @Override
    public void onApplicationEvent(ApplicationStartedEvent event) {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        reconciliation.reconcile()
                .timeout(timeout)
                .subscribe(report -> state.set(report.healthy()
                        ? Health.up().withDetail("checkedFiles", report.checkedFiles()).build()
                                : Health.down().withDetail("missingObjectKeys", report.missingObjectKeys())
                                .withDetail("orphanObjectKeys", report.orphanObjectKeys())
                                .withDetail("orphanDeletionEnabled", report.orphanDeletionEnabled())
                                .withDetail("capacityExceeded", report.capacityExceeded()).build()),
                        error -> {
                            state.set(Health.down().withDetail("reason", "storage reconciliation failed").build());
                            log.error("PostgreSQL cutover storage reconciliation failed", error);
                        });
    }

    @Override
    public Mono<Health> health() {
        return Mono.just(state.get());
    }
}
