package auto.parto.persistence;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface NotificationRepository {

    Mono<NotificationRecord> enqueue(UUID recipientUserPublicId, String eventType, String payloadJson);

    Flux<NotificationRecord> findPending(int limit);

    Mono<Long> countPending();

    Flux<UUID> findPendingRecipientIds(int limit);

    Flux<NotificationRecord> findPendingForRecipient(UUID recipientUserPublicId, int limit);

    Mono<Long> markPendingForRecipient(UUID recipientUserPublicId);

    Mono<Boolean> markPendingForRecipientAndId(UUID recipientUserPublicId, String notificationId);

    Mono<Boolean> updateStatus(UUID notificationPublicId, String status);

    Mono<NotificationAttemptRecord> recordAttempt(UUID notificationPublicId, String channel, String status,
                                                  String providerReference, String failureReason);
}
