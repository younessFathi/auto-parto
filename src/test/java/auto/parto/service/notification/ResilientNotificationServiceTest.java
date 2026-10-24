package auto.parto.service.notification;

import auto.parto.dto.NotificationRequest;
import auto.parto.enums.NotificationType;
import auto.parto.persistence.NotificationRecord;
import auto.parto.service.BasicCircuitBreaker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResilientNotificationServiceTest {

    @Mock
    private BasicCircuitBreaker circuitBreaker;
    @Mock
    private SSENotificationService sseNotificationService;
    @Mock
    private HybridNotificationStore notificationStore;

    @Test
    void sendsUsingThePostgresqlPublicIdAfterDurablePersistence() {
        UUID userId = UUID.randomUUID();
        UUID persistedId = UUID.randomUUID();
        NotificationRequest request = new NotificationRequest("caller-id", userId.toString(),
                NotificationType.NEW_SEARCH_POSTED, "safe", 1L);
        NotificationRecord record = new NotificationRecord(persistedId, userId, "NEW_SEARCH_POSTED", "{}",
                "PENDING", Instant.now(), Instant.now());
        when(notificationStore.storePendingRecord(request)).thenReturn(Mono.just(record));
        when(notificationStore.claimNotification(eq(persistedId), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(Mono.just(record));
        when(sseNotificationService.sendViaSSE(userId.toString(), persistedId, request)).thenReturn(true);
        when(notificationStore.recordAttempt(eq(persistedId), eq("SSE"), eq("WRITTEN"), eq(null), eq(null)))
                .thenReturn(Mono.empty());
        when(notificationStore.acknowledgeDelivery(eq(persistedId), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Mono.just(true));

        StepVerifier.create(new ResilientNotificationService(circuitBreaker, sseNotificationService, notificationStore)
                        .sendNotification(request))
                .expectNext(true)
                .verifyComplete();

        verify(sseNotificationService).sendViaSSE(eq(userId.toString()), eq(persistedId), eq(request));
        verify(notificationStore).recordAttempt(eq(persistedId), eq("SSE"), eq("WRITTEN"),
                eq(null), eq(null));
        verify(notificationStore).acknowledgeDelivery(eq(persistedId), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void reportsFailureWhenDurablePersistenceDoesNotProduceARecord() {
        NotificationRequest request = new NotificationRequest("caller-id", UUID.randomUUID().toString(),
                NotificationType.NEW_SEARCH_POSTED, "safe", 1L);
        when(notificationStore.storePendingRecord(request)).thenReturn(Mono.empty());

        StepVerifier.create(new ResilientNotificationService(circuitBreaker, sseNotificationService, notificationStore)
                        .sendNotification(request))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void failedSocketRetainsTheSameDurableRecordWithoutASecondEnqueue() {
        UUID userId = UUID.randomUUID();
        UUID persistedId = UUID.randomUUID();
        NotificationRequest request = new NotificationRequest("caller-id", userId.toString(),
                NotificationType.NEW_SEARCH_POSTED, "safe", 1L);
        NotificationRecord record = new NotificationRecord(persistedId, userId, "NEW_SEARCH_POSTED", "{}",
                "PENDING", Instant.now(), Instant.now());
        when(notificationStore.storePendingRecord(request)).thenReturn(Mono.just(record));
        when(notificationStore.claimNotification(eq(persistedId), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(Mono.just(record));
        when(sseNotificationService.sendViaSSE(userId.toString(), persistedId, request)).thenReturn(false);
        when(notificationStore.recordAttempt(eq(persistedId), eq("SSE"), eq("FAILED"), eq(null),
                eq("DELIVERY_UNAVAILABLE"))).thenReturn(Mono.empty());
        when(notificationStore.scheduleRetry(eq(persistedId), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), eq("DELIVERY_UNAVAILABLE"), eq(3)))
                .thenReturn(Mono.just(true));

        StepVerifier.create(new ResilientNotificationService(circuitBreaker, sseNotificationService, notificationStore)
                        .sendNotification(request))
                .expectNext(false)
                .verifyComplete();

        verify(notificationStore).storePendingRecord(request);
        verify(notificationStore, org.mockito.Mockito.never()).storePending(request);
        verify(notificationStore).scheduleRetry(eq(persistedId), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), eq("DELIVERY_UNAVAILABLE"), eq(3));
    }

    @Test
    void socketExceptionKeepsThePersistedRecordRetryable() {
        UUID userId = UUID.randomUUID();
        UUID persistedId = UUID.randomUUID();
        NotificationRequest request = new NotificationRequest("caller-id", userId.toString(),
                NotificationType.NEW_SEARCH_POSTED, "safe", 1L);
        NotificationRecord record = pendingRecord(persistedId, userId);
        stubClaim(request, record);
        when(sseNotificationService.sendViaSSE(userId.toString(), persistedId, request))
                .thenThrow(new IllegalStateException("socket failed"));
        stubRetry(persistedId, "DELIVERY_ERROR");

        StepVerifier.create(new ResilientNotificationService(circuitBreaker, sseNotificationService, notificationStore)
                        .deliverDirect(request))
                .assertNext(result -> {
                    org.assertj.core.api.Assertions.assertThat(result.publicId()).isEqualTo(persistedId);
                    org.assertj.core.api.Assertions.assertThat(result.persisted()).isTrue();
                    org.assertj.core.api.Assertions.assertThat(result.delivered()).isFalse();
                })
                .verifyComplete();

        verify(notificationStore).scheduleRetry(eq(persistedId), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), eq("DELIVERY_ERROR"), eq(3));
    }

    @Test
    void attemptWriteExceptionStillSchedulesTheSameClaim() {
        UUID userId = UUID.randomUUID();
        UUID persistedId = UUID.randomUUID();
        NotificationRequest request = new NotificationRequest("caller-id", userId.toString(),
                NotificationType.NEW_SEARCH_POSTED, "safe", 1L);
        NotificationRecord record = pendingRecord(persistedId, userId);
        stubClaim(request, record);
        when(sseNotificationService.sendViaSSE(userId.toString(), persistedId, request)).thenReturn(true);
        when(notificationStore.recordAttempt(eq(persistedId), eq("SSE"), eq("WRITTEN"), eq(null), eq(null)))
                .thenReturn(Mono.error(new IllegalStateException("attempt write failed")));
        stubRetry(persistedId, "DELIVERY_ERROR");

        StepVerifier.create(new ResilientNotificationService(circuitBreaker, sseNotificationService, notificationStore)
                        .deliverDirect(request))
                .expectNextMatches(result -> result.publicId().equals(persistedId)
                        && result.persisted() && !result.delivered())
                .verifyComplete();

        verify(notificationStore).scheduleRetry(eq(persistedId), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), eq("DELIVERY_ERROR"), eq(3));
    }

    @Test
    void acknowledgementExceptionStillReturnsAcceptedDurableResultAndSchedulesRetry() {
        UUID userId = UUID.randomUUID();
        UUID persistedId = UUID.randomUUID();
        NotificationRequest request = new NotificationRequest("caller-id", userId.toString(),
                NotificationType.NEW_SEARCH_POSTED, "safe", 1L);
        NotificationRecord record = pendingRecord(persistedId, userId);
        stubClaim(request, record);
        when(sseNotificationService.sendViaSSE(userId.toString(), persistedId, request)).thenReturn(true);
        when(notificationStore.recordAttempt(eq(persistedId), eq("SSE"), eq("WRITTEN"), eq(null), eq(null)))
                .thenReturn(Mono.empty());
        when(notificationStore.acknowledgeDelivery(eq(persistedId), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Mono.error(new IllegalStateException("ack failed")));
        stubRetry(persistedId, "DELIVERY_ERROR");

        StepVerifier.create(new ResilientNotificationService(circuitBreaker, sseNotificationService, notificationStore)
                        .deliverDirect(request))
                .expectNextMatches(result -> result.publicId().equals(persistedId)
                        && result.persisted() && !result.delivered())
                .verifyComplete();

        verify(notificationStore).scheduleRetry(eq(persistedId), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), eq("DELIVERY_ERROR"), eq(3));
    }

    private NotificationRecord pendingRecord(UUID publicId, UUID userId) {
        return new NotificationRecord(publicId, userId, "NEW_SEARCH_POSTED", "{}", "PENDING",
                Instant.now(), Instant.now());
    }

    private void stubClaim(NotificationRequest request, NotificationRecord record) {
        when(notificationStore.storePendingRecord(request)).thenReturn(Mono.just(record));
        when(notificationStore.claimNotification(eq(record.publicId()), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(Mono.just(record));
    }

    private void stubRetry(UUID publicId, String failureCode) {
        when(notificationStore.recordAttempt(eq(publicId), eq("SSE"), eq("FAILED"), eq(null), eq(failureCode)))
                .thenReturn(Mono.error(new IllegalStateException("failure record unavailable")));
        when(notificationStore.scheduleRetry(eq(publicId), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), eq(failureCode), eq(3))).thenReturn(Mono.just(true));
    }
}
