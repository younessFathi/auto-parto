package auto.parto.audit;

import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class AuditPublisher {

    private final AuditOutboxRepository outbox;
    private final AuditStore auditStore;
    private final Clock clock;
    private final int maxAttempts;
    private final int maxConcurrency;
    private final Duration baseRetryDelay;
    private final Duration maxRetryDelay;
    private final AuditReplayAuthorizer replayAuthorizer;

    public AuditPublisher(
            AuditOutboxRepository outbox,
            AuditStore auditStore,
            Clock clock,
            int maxAttempts,
            int maxConcurrency,
            Duration baseRetryDelay,
            Duration maxRetryDelay,
            AuditReplayAuthorizer replayAuthorizer) {
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.auditStore = Objects.requireNonNull(auditStore, "auditStore");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (maxAttempts < 1 || maxConcurrency < 1) {
            throw new IllegalArgumentException("attempts and concurrency must be positive");
        }
        this.maxAttempts = maxAttempts;
        this.maxConcurrency = maxConcurrency;
        this.baseRetryDelay = requirePositive(baseRetryDelay, "baseRetryDelay");
        this.maxRetryDelay = requirePositive(maxRetryDelay, "maxRetryDelay");
        this.replayAuthorizer = Objects.requireNonNull(replayAuthorizer, "replayAuthorizer");
    }

    public Mono<AuditPublishReport> publishBatch(String workerId, int limit, Duration leaseDuration) {
        if (workerId == null || workerId.isBlank() || limit < 1) {
            return Mono.error(new IllegalArgumentException("workerId and limit are required"));
        }
        try {
            AuditPersistenceLimits.requireBatchSize(limit);
        } catch (IllegalArgumentException exception) {
            return Mono.error(exception);
        }
        Instant now = clock.instant();
        return outbox.claimDue(workerId, limit, now, leaseDuration)
                .flatMap(this::publishOne, maxConcurrency)
                .collectList()
                .map(outcomes -> new AuditPublishReport(
                        outcomes.stream().filter(outcome -> outcome == Outcome.PUBLISHED).count(),
                        outcomes.stream().filter(outcome -> outcome == Outcome.RETRIED).count(),
                        outcomes.stream().filter(outcome -> outcome == Outcome.EXHAUSTED).count()));
    }

    public Mono<Void> replay(UUID eventId, String requester) {
        Objects.requireNonNull(eventId, "eventId");
        if (requester == null || requester.isBlank()) {
            return Mono.error(new IllegalArgumentException("replay requester is required"));
        }
        return replayAuthorizer.authorize(requester, eventId)
                .filter(Boolean.TRUE::equals)
                .switchIfEmpty(Mono.error(new SecurityException("audit replay is not authorized")))
                .then(outbox.findByEventId(eventId))
                .switchIfEmpty(Mono.error(new IllegalArgumentException("audit event was not found")))
                .flatMap(record -> outbox.claimForReplay(
                                record.deliveryId(), "replay-" + eventId, clock.instant(), Duration.ofMinutes(1))
                        .switchIfEmpty(Mono.error(new IllegalStateException("audit delivery is already claimed")))
                        .flatMap(claimed -> auditStore.append(claimed.event())
                                .then(outbox.markDelivered(
                                        claimed.deliveryId(), claimed.claimOwner(), clock.instant()))));
    }

    public Mono<Long> reconcile(Instant since, int limit) {
        Objects.requireNonNull(since, "since");
        if (limit < 1) {
            return Mono.error(new IllegalArgumentException("limit must be positive"));
        }
        try {
            AuditPersistenceLimits.requireBatchSize(limit);
        } catch (IllegalArgumentException exception) {
            return Mono.error(exception);
        }
        return outbox.findDispatchedSince(since, limit)
                .flatMap(record -> auditStore.contains(record.event().eventId())
                        .flatMap(known -> known
                                ? Mono.just(0L)
                                        : outbox.markForReplay(record.deliveryId(), clock.instant())
                                        .thenReturn(1L)))
                .reduce(0L, Long::sum);
    }

    public Mono<Integer> purgeTerminal(int limit) {
        if (limit < 1) {
            return Mono.error(new IllegalArgumentException("limit must be positive"));
        }
        try {
            AuditPersistenceLimits.requireBatchSize(limit);
        } catch (IllegalArgumentException exception) {
            return Mono.error(exception);
        }
        return outbox.purgeTerminalBefore(clock.instant(), limit);
    }

    private Mono<Outcome> publishOne(AuditOutboxRecord record) {
        return auditStore.append(record.event())
                .then(outbox.markDelivered(record.deliveryId(), record.claimOwner(), clock.instant()))
                .thenReturn(Outcome.PUBLISHED)
                .onErrorResume(error -> handleFailure(record, error));
    }

    private Mono<Outcome> handleFailure(AuditOutboxRecord record, Throwable error) {
        boolean retryable = !(error instanceof AuditDeliveryException deliveryException)
                || deliveryException.retryable();
        String failureCode = classify(error);
        if (!retryable || record.attemptCount() >= maxAttempts) {
            return outbox.markExhausted(record.deliveryId(), record.claimOwner(), failureCode)
                    .thenReturn(Outcome.EXHAUSTED);
        }
        return outbox.scheduleRetry(
                        record.deliveryId(), record.claimOwner(),
                        clock.instant().plus(retryDelay(record.attemptCount())),
                        failureCode)
                .thenReturn(Outcome.RETRIED);
    }

    private Duration retryDelay(int attempt) {
        Duration delay = baseRetryDelay;
        for (int index = 1; index < attempt; index++) {
            if (delay.compareTo(maxRetryDelay) >= 0) {
                return maxRetryDelay;
            }
            Duration doubled;
            try {
                doubled = delay.multipliedBy(2);
            } catch (ArithmeticException exception) {
                return maxRetryDelay;
            }
            delay = doubled.compareTo(maxRetryDelay) > 0 ? maxRetryDelay : doubled;
        }
        return delay;
    }

    private static String classify(Throwable error) {
        if (error instanceof AuditDeliveryException deliveryException && !deliveryException.retryable()) {
            return "AUDIT_DELIVERY_PERMANENT";
        }
        if (error instanceof AuditDeliveryException) {
            return "AUDIT_DELIVERY_RETRYABLE";
        }
        return "AUDIT_DELIVERY_UNKNOWN";
    }

    private static Duration requirePositive(Duration value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }

    private enum Outcome {
        PUBLISHED,
        RETRIED,
        EXHAUSTED
    }
}
