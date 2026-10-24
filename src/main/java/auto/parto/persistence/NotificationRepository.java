package auto.parto.persistence;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.time.Duration;
import java.time.Instant;

public interface NotificationRepository {

    Mono<NotificationRecord> enqueue(UUID recipientUserPublicId, String eventType, String payloadJson);

    Flux<NotificationRecord> findPending(int limit);

    Mono<Long> countPending();

    Flux<UUID> findPendingRecipientIds(int limit);

    Flux<UUID> findPendingRecipientIds(UUID afterRecipientPublicId, int limit);

    Flux<NotificationRecord> findPendingForRecipient(UUID recipientUserPublicId, int limit);

    Flux<NotificationRecord> findForRecipientAfter(UUID recipientUserPublicId, UUID afterNotificationPublicId,
                                                    int limit);

    Mono<Boolean> cursorBelongsToRecipient(UUID recipientUserPublicId, UUID notificationPublicId);

    Mono<Long> markPendingForRecipient(UUID recipientUserPublicId);

    Mono<Boolean> markPendingForRecipientAndId(UUID recipientUserPublicId, String notificationId);

    Mono<Boolean> updateStatus(UUID notificationPublicId, String status);

    Mono<NotificationAttemptRecord> recordAttempt(UUID notificationPublicId, String channel, String status,
                                                  String providerReference, String failureReason);

    Flux<NotificationRecord> claimPendingForRecipient(UUID recipientUserPublicId, String claimOwner,
                                                       Instant now, Duration leaseDuration, int limit);

    Mono<Boolean> acknowledgeDelivery(UUID notificationPublicId, String claimOwner);

    Mono<Boolean> scheduleRetry(UUID notificationPublicId, String claimOwner, Instant nextAttemptAt,
                                String failureCode, int maxAttempts);
}
