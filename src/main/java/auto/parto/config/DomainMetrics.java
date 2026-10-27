package auto.parto.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/** Low-cardinality domain metrics. Values that can contain identifiers are never accepted as tags. */
@Component
public final class DomainMetrics {

    private static final AtomicInteger REGISTERED_SSE_CONNECTIONS = new AtomicInteger();

    static {
        io.micrometer.core.instrument.Gauge.builder("auto.parto.sse.connections.registered",
                        REGISTERED_SSE_CONNECTIONS, AtomicInteger::get)
                .description("Currently registered SSE streams for this application process")
                .register(Metrics.globalRegistry);
    }

    public DomainMetrics(MeterRegistry applicationRegistry) {
        Metrics.addRegistry(applicationRegistry);
    }

    public static <T> Mono<T> observe(String domain, String operation, Mono<T> source) {
        return Mono.defer(() -> {
            Timer.Sample sample = Timer.start(Metrics.globalRegistry);
            return source.doFinally(signal -> stop(sample, domain, operation, outcome(signal)));
        });
    }

    public static <T> Flux<T> observe(String domain, String operation, Flux<T> source) {
        return Flux.defer(() -> {
            Timer.Sample sample = Timer.start(Metrics.globalRegistry);
            return source.doFinally(signal -> stop(sample, domain, operation, outcome(signal)));
        });
    }

    public static void walletMutation(String operation, String outcome) {
        Counter.builder("auto.parto.wallet.mutations")
                .description("Wallet mutation outcomes")
                .tag("operation", oneOf(operation, "credit", "debit"))
                .tag("outcome", oneOf(outcome, "success", "failure"))
                .register(Metrics.globalRegistry).increment();
    }

    public static void notificationDelivery(String channel, String outcome) {
        Counter.builder("auto.parto.notification.deliveries")
                .description("Durable notification delivery outcomes")
                .tag("channel", oneOf(channel, "sse", "fcm", "email"))
                .tag("outcome", oneOf(outcome, "acknowledged", "retry_scheduled", "exhausted", "failure"))
                .register(Metrics.globalRegistry).increment();
    }

    public static void sseConnectionRejected() {
        sseConnection("rejected");
    }

    public static void sseConnectionOpened() {
        sseConnection("opened");
    }

    public static void sseConnectionClosed() {
        sseConnection("closed");
    }

    public static void sseEmit(String outcome) {
        sseCounter(oneOf(outcome, "success", "overflow", "no_subscriber", "cancelled", "terminated", "failure"));
    }

    public static void setRegisteredSseConnections(int count) {
        REGISTERED_SSE_CONNECTIONS.set(Math.max(0, count));
    }

    public static void storageUploadBytes(long bytes) {
        if (bytes < 0) return;
        DistributionSummary.builder("auto.parto.storage.upload.bytes")
                .description("Uploaded object size in bytes")
                .baseUnit("bytes")
                .register(Metrics.globalRegistry).record(bytes);
    }

    public static void storageOperation(String operation, String outcome) {
        Counter.builder("auto.parto.storage.operations")
                .description("Object storage operation outcomes")
                .tag("operation", oneOf(operation, "put", "read", "head", "delete", "list", "scan"))
                .tag("outcome", oneOf(outcome, "success", "failure", "cancelled"))
                .register(Metrics.globalRegistry).increment();
    }

    public static <T> Mono<T> storageCall(String operation, Mono<T> source) {
        return observe("storage", operation, source)
                .doOnSuccess(ignored -> storageOperation(operation, "success"))
                .doOnError(ignored -> storageOperation(operation, "failure"))
                .doOnCancel(() -> storageOperation(operation, "cancelled"));
    }

    public static <T> Flux<T> storageCall(String operation, Flux<T> source) {
        return observe("storage", operation, source)
                .doOnComplete(() -> storageOperation(operation, "success"))
                .doOnError(ignored -> storageOperation(operation, "failure"))
                .doOnCancel(() -> storageOperation(operation, "cancelled"));
    }

    private static void sseCounter(String outcome) {
        Counter.builder("auto.parto.sse.events")
                .description("SSE stream registration and emission outcomes")
                .tag("outcome", outcome)
                .register(Metrics.globalRegistry).increment();
    }

    private static void sseConnection(String outcome) {
        Counter.builder("auto.parto.sse.connections")
                .description("SSE connection lifecycle outcomes")
                .tag("outcome", outcome)
                .register(Metrics.globalRegistry).increment();
    }

    private static void stop(Timer.Sample sample, String domain, String operation, String outcome) {
        sample.stop(Timer.builder("auto.parto.domain.operation")
                .description("Domain operation duration by bounded outcome")
                .tag("domain", oneOf(domain, "wallet", "notification", "storage"))
                .tag("operation", oneOf(operation, "mutation", "delivery", "put", "read", "head", "delete", "list", "scan"))
                .tag("outcome", outcome)
                .register(Metrics.globalRegistry));
    }

    private static String outcome(SignalType signal) {
        return switch (signal) {
            case ON_COMPLETE -> "success";
            case ON_ERROR -> "failure";
            case CANCEL -> "cancelled";
            default -> "failure";
        };
    }

    private static String oneOf(String value, String... allowed) {
        if (value != null) {
            for (String candidate : allowed) {
                if (candidate.equals(value)) return value;
            }
        }
        return "other";
    }
}
