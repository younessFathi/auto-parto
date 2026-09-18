package auto.parto.audit;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuditPublisherTest {

    private static final Instant NOW = Instant.parse("2026-09-15T12:00:00Z");

    @Test
    void publishesAndMarksTheEventDelivered() {
        FakeOutbox outbox = new FakeOutbox();
        FakeAuditStore store = new FakeAuditStore();
        AuditEvent event = event();
        outbox.due.add(record(event, 0));

        AuditPublishReport report = publisher(outbox, store)
                .publishBatch("worker-1", 10, Duration.ofSeconds(30))
                .block();

        assertThat(report).isEqualTo(new AuditPublishReport(1, 0, 0));
        assertThat(store.events).containsExactly(event.eventId());
        assertThat(outbox.delivered).containsExactly(event.eventId());
    }

    @Test
    void duplicateAuditStoreAcknowledgementIsSuccessful() {
        FakeOutbox outbox = new FakeOutbox();
        FakeAuditStore store = new FakeAuditStore();
        store.result = AuditAppendResult.DUPLICATE;
        AuditEvent event = event();
        outbox.due.add(record(event, 2));

        AuditPublishReport report = publisher(outbox, store)
                .publishBatch("worker-1", 10, Duration.ofSeconds(30))
                .block();

        assertThat(report.published()).isEqualTo(1);
        assertThat(outbox.delivered).containsExactly(event.eventId());
        assertThat(outbox.retries).isEmpty();
    }

    @Test
    void retryableFailureSchedulesBoundedRetry() {
        FakeOutbox outbox = new FakeOutbox();
        FakeAuditStore store = new FakeAuditStore();
        store.failure = new AuditDeliveryException("audit store unavailable", true);
        AuditEvent event = event();
        outbox.due.add(record(event, 0));

        AuditPublishReport report = publisher(outbox, store)
                .publishBatch("worker-1", 10, Duration.ofSeconds(30))
                .block();

        assertThat(report.retried()).isEqualTo(1);
        assertThat(outbox.retries).containsExactly(event.eventId());
        assertThat(outbox.exhausted).isEmpty();
        assertThat(outbox.nextRetry).isEqualTo(NOW.plusSeconds(2));
    }

    @Test
    void permanentFailureExhaustsTheEvent() {
        FakeOutbox outbox = new FakeOutbox();
        FakeAuditStore store = new FakeAuditStore();
        store.failure = new AuditDeliveryException("invalid audit payload", false);
        AuditEvent event = event();
        outbox.due.add(record(event, 0));

        AuditPublishReport report = publisher(outbox, store)
                .publishBatch("worker-1", 10, Duration.ofSeconds(30))
                .block();

        assertThat(report.exhausted()).isEqualTo(1);
        assertThat(outbox.exhausted).containsExactly(event.eventId());
        assertThat(outbox.retries).isEmpty();
    }

    @Test
    void replayRepublishesByStableEventId() {
        FakeOutbox outbox = new FakeOutbox();
        FakeAuditStore store = new FakeAuditStore();
        AuditEvent event = event();
        outbox.records.add(record(event, 4));

        StepVerifier.create(publisher(outbox, store).replay(event.eventId()))
                .verifyComplete();

        assertThat(store.events).containsExactly(event.eventId());
        assertThat(outbox.delivered).containsExactly(event.eventId());
    }

    @Test
    void reconciliationSchedulesMissingDispatchedEventsForReplay() {
        FakeOutbox outbox = new FakeOutbox();
        FakeAuditStore store = new FakeAuditStore();
        AuditEvent present = event();
        AuditEvent missing = new AuditEvent(
                UUID.randomUUID(), "USER_STATUS_CHANGED", 1, NOW, null, "USER", UUID.randomUUID(),
                null, "test", "{}");
        outbox.dispatched.add(record(present, 0));
        outbox.dispatched.add(record(missing, 0));
        store.known.add(present.eventId());

        StepVerifier.create(publisher(outbox, store).reconcile(NOW.minusSeconds(60), 10))
                .expectNext(1L)
                .verifyComplete();

        assertThat(outbox.replay).containsExactly(missing.eventId());
    }

    @Test
    void rejectsUnboundedBatchRequests() {
        StepVerifier.create(publisher(new FakeOutbox(), new FakeAuditStore())
                        .publishBatch("worker-1", AuditPersistenceLimits.MAX_BATCH_SIZE + 1,
                                Duration.ofSeconds(30)))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    private static AuditPublisher publisher(FakeOutbox outbox, FakeAuditStore store) {
        return new AuditPublisher(
                outbox,
                store,
                Clock.fixed(NOW, ZoneOffset.UTC),
                3,
                2,
                Duration.ofSeconds(2),
                Duration.ofMinutes(1));
    }

    private static AuditEvent event() {
        return new AuditEvent(
                UUID.randomUUID(), "USER_STATUS_CHANGED", 1, NOW, null, "USER", UUID.randomUUID(),
                UUID.randomUUID(), "test", "{}");
    }

    private static AuditOutboxRecord record(AuditEvent event, int attempts) {
        return new AuditOutboxRecord(UUID.randomUUID(), "AUDIT", null, event, attempts);
    }

    private static final class FakeOutbox implements AuditOutboxRepository {
        private final List<AuditOutboxRecord> due = new ArrayList<>();
        private final List<AuditOutboxRecord> records = new ArrayList<>();
        private final List<AuditOutboxRecord> dispatched = new ArrayList<>();
        private final List<UUID> delivered = new ArrayList<>();
        private final List<UUID> retries = new ArrayList<>();
        private final List<UUID> exhausted = new ArrayList<>();
        private final List<UUID> replay = new ArrayList<>();
        private Instant nextRetry;

        @Override
        public Flux<AuditOutboxRecord> claimDue(String workerId, int limit, Instant now, Duration leaseDuration) {
            return Flux.fromIterable(due.stream().limit(limit).toList());
        }

        @Override
        public Mono<AuditOutboxRecord> claimForReplay(
                UUID deliveryId, String workerId, Instant now, Duration leaseDuration) {
            return Mono.just(eventFor(deliveryId))
                    .map(record -> new AuditOutboxRecord(
                            record.deliveryId(), record.destination(), workerId + ":claim",
                            record.event(), record.attemptCount() + 1));
        }

        @Override
        public Mono<AuditOutboxRecord> findByEventId(UUID eventId) {
            return Flux.fromIterable(records)
                    .filter(record -> record.event().eventId().equals(eventId))
                    .next();
        }

        @Override
        public Flux<AuditOutboxRecord> findDispatchedSince(Instant since, int limit) {
            return Flux.fromIterable(dispatched.stream().limit(limit).toList());
        }

        @Override
        public Mono<Void> markDelivered(UUID deliveryId, String claimOwner, Instant deliveredAt) {
            delivered.add(eventFor(deliveryId).event().eventId());
            return Mono.empty();
        }

        @Override
        public Mono<Void> scheduleRetry(UUID deliveryId, String claimOwner, Instant availableAt, String failureCode) {
            retries.add(eventFor(deliveryId).event().eventId());
            nextRetry = availableAt;
            return Mono.empty();
        }

        @Override
        public Mono<Void> markExhausted(UUID deliveryId, String claimOwner, String failureCode) {
            exhausted.add(eventFor(deliveryId).event().eventId());
            return Mono.empty();
        }

        @Override
        public Mono<Void> markForReplay(UUID deliveryId, Instant availableAt) {
            replay.add(eventFor(deliveryId).event().eventId());
            due.add(eventFor(deliveryId));
            return Mono.empty();
        }

        private AuditOutboxRecord eventFor(UUID deliveryId) {
            return Flux.concat(Flux.fromIterable(due), Flux.fromIterable(records), Flux.fromIterable(dispatched))
                    .filter(record -> record.deliveryId().equals(deliveryId))
                    .next()
                    .block();
        }
    }

    private static final class FakeAuditStore implements AuditStore {
        private final List<UUID> events = new ArrayList<>();
        private final List<UUID> known = new ArrayList<>();
        private AuditAppendResult result = AuditAppendResult.CREATED;
        private AuditDeliveryException failure;

        @Override
        public Mono<AuditAppendResult> append(AuditEvent event) {
            return Mono.defer(() -> {
                if (failure != null) {
                    return Mono.error(failure);
                }
                events.add(event.eventId());
                known.add(event.eventId());
                return Mono.just(result);
            });
        }

        @Override
        public Mono<Boolean> contains(UUID eventId) {
            return Mono.just(known.contains(eventId));
        }
    }
}
