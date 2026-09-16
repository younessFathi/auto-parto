package auto.parto.audit;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public interface AuditOutboxRepository {

    Flux<AuditOutboxRecord> claimDue(String workerId, int limit, Instant now, Duration leaseDuration);

    Mono<AuditOutboxRecord> findByEventId(UUID eventId);

    Flux<AuditOutboxRecord> findDispatchedSince(Instant since, int limit);

    Mono<Void> markDelivered(UUID deliveryId, Instant deliveredAt);

    Mono<Void> scheduleRetry(UUID deliveryId, Instant availableAt, String failureCode);

    Mono<Void> markExhausted(UUID deliveryId, String failureCode);

    Mono<Void> markForReplay(UUID deliveryId, Instant availableAt);
}
