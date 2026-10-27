package auto.parto.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

import java.util.concurrent.CancellationException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Slf4j
public final class ProviderCallTelemetry {

    private final MeterRegistry registry;
    private final ObservationRegistry observationRegistry;

    @Autowired
    public ProviderCallTelemetry(MeterRegistry registry, ObservationRegistry observationRegistry) {
        this.registry = registry;
        this.observationRegistry = observationRegistry;
    }

    public ProviderCallTelemetry(MeterRegistry registry) {
        this(registry, ObservationRegistry.NOOP);
    }

    public <T> Mono<T> observe(String provider, String operation, Mono<T> source, Duration timeout) {
        return Mono.defer(() -> {
            Timer.Sample sample = Timer.start(registry);
            AtomicBoolean recorded = new AtomicBoolean();
            Mono<T> observed = ObservationSupport.observe(observationRegistry, "external.provider",
                    java.util.Map.of("provider", provider, "operation", operation), source.timeout(timeout));
            return observed
                    .doOnSuccess(ignored -> finish(sample, recorded, provider, operation, "success"))
                    .doOnError(error -> finish(sample, recorded, provider, operation, outcome(error)))
                    .doFinally(signal -> finishCancellation(sample, recorded, provider, operation, signal));
        });
    }

    public <T> Flux<T> observe(String provider, String operation, Flux<T> source, Duration timeout) {
        return Flux.defer(() -> {
            Timer.Sample sample = Timer.start(registry);
            AtomicBoolean recorded = new AtomicBoolean();
            Flux<T> observed = ObservationSupport.observe(observationRegistry, "external.provider",
                    java.util.Map.of("provider", provider, "operation", operation), source.timeout(timeout));
            return observed
                    .doOnComplete(() -> finish(sample, recorded, provider, operation, "success"))
                    .doOnError(error -> finish(sample, recorded, provider, operation, outcome(error)))
                    .doFinally(signal -> finishCancellation(sample, recorded, provider, operation, signal));
        });
    }

    private void finishCancellation(Timer.Sample sample, AtomicBoolean recorded, String provider,
                                    String operation, SignalType signal) {
        if (signal == SignalType.CANCEL) {
            finish(sample, recorded, provider, operation, "cancelled");
        }
    }

    private void finish(Timer.Sample sample, AtomicBoolean recorded, String provider,
                        String operation, String outcome) {
        if (!recorded.compareAndSet(false, true)) return;
        sample.stop(Timer.builder("auto.parto.provider.calls")
                .description("External provider call outcomes and duration")
                .tag("provider", provider)
                .tag("operation", operation)
                .tag("outcome", outcome)
                .register(registry));
        if (!"success".equals(outcome) && !"cancelled".equals(outcome)) {
            log.warn("External provider call completed with non-success outcome provider={} operation={} outcome={}",
                    provider, operation, outcome);
        }
    }

    private String outcome(Throwable error) {
        if (error instanceof TimeoutException) return "timeout";
        if (error instanceof RejectedExecutionException) return "saturated";
        if (error instanceof CancellationException) return "cancelled";
        return "failure";
    }
}
