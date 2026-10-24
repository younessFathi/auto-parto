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
        when(sseNotificationService.sendViaSSE(userId.toString(), persistedId, request)).thenReturn(true);

        StepVerifier.create(new ResilientNotificationService(circuitBreaker, sseNotificationService, notificationStore)
                        .sendNotification(request))
                .expectNext(true)
                .verifyComplete();

        verify(sseNotificationService).sendViaSSE(eq(userId.toString()), eq(persistedId), eq(request));
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
}
