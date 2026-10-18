package auto.parto.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

public final class AuditOutboxWorker implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(AuditOutboxWorker.class);

    private final AuditPublisher publisher;
    private final AuditProcessingProperties properties;
    private final Clock clock;
    private Disposable subscription;
    private volatile boolean running;

    public AuditOutboxWorker(AuditPublisher publisher, AuditProcessingProperties properties, Clock clock) {
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void start() {
        if (running) {
            return;
        }
        running = true;
        subscription = Flux.merge(
                        schedule(properties.getPollInterval(), this::publish),
                        schedule(properties.getReconciliationInterval(), this::reconcile),
                        schedule(properties.getCleanupInterval(), this::cleanup))
                .subscribe(value -> { }, error -> log.error("Audit outbox worker stopped", error));
    }

    @Override
    public void stop() {
        running = false;
        if (subscription != null) {
            subscription.dispose();
            subscription = null;
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    private Mono<AuditPublishReport> publish() {
        return whenReady(() -> publisher.publishBatch(
                        properties.getWorkerId(), properties.getBatchSize(), properties.getLeaseDuration()))
                .doOnNext(report -> log.debug("Audit outbox batch completed: {}", report))
                .onErrorResume(error -> reportFailure("publish", error));
    }

    private Mono<Long> reconcile() {
        return whenReady(() -> publisher.reconcile(
                        clock.instant().minus(properties.getReconciliationLookback()), properties.getBatchSize()))
                .doOnNext(count -> log.debug("Audit reconciliation scheduled {} replay(s)", count))
                .onErrorResume(error -> reportFailure("reconcile", error));
    }

    private Mono<Integer> cleanup() {
        return whenReady(() -> publisher.purgeTerminal(properties.getBatchSize()))
                .doOnNext(count -> log.debug("Audit cleanup removed {} terminal delivery row(s)", count))
                .onErrorResume(error -> reportFailure("cleanup", error));
    }

    private <T> Mono<T> whenReady(java.util.function.Supplier<Mono<T>> task) {
        return publisher.readiness()
                .timeout(properties.getReadinessTimeout())
                .doOnError(error -> log.warn("Audit database is unavailable; worker will not claim deliveries"))
                .then(Mono.defer(task));
    }

    private <T> Flux<T> schedule(java.time.Duration interval, java.util.function.Supplier<Mono<T>> task) {
        return Flux.interval(interval)
                .onBackpressureDrop()
                .concatMap(ignored -> task.get())
                .onErrorResume(error -> {
                    log.error("Audit {} task failed", task, error);
                    return Flux.empty();
                });
    }

    private <T> Mono<T> reportFailure(String operation, Throwable error) {
        log.warn("Audit outbox {} failed; the next scheduled run will retry", operation, error);
        return Mono.empty();
    }
}
