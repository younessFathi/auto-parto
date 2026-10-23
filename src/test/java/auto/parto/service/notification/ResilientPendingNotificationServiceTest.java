package auto.parto.service.notification;

import auto.parto.service.BasicCircuitBreaker;
import auto.parto.dto.NotificationRequest;
import auto.parto.enums.NotificationType;
import auto.parto.persistence.NotificationRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Instant;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResilientPendingNotificationServiceTest {

    @Mock
    private HybridNotificationStore notificationStore;
    @Mock
    private SSENotificationService sseService;
    @Mock
    private BasicCircuitBreaker circuitBreaker;

    @InjectMocks
    private ResilientPendingNotificationService service;

    @Test
    void recoverOnStartupSubscribesAndProcessesPendingNotifications() throws Exception {
        String userId = "00000000-0000-0000-0000-000000000001";
        Map<String, Object> stats = Map.of(
                "totalNotifications", 2L,
                "totalUsers", 1L
        );
        when(notificationStore.getPendingStats()).thenReturn(Mono.just(stats));
        when(notificationStore.getPendingRecipientIds(null, 100)).thenReturn(Flux.just(userId));
        when(notificationStore.rebuildProjection(userId)).thenReturn(Mono.empty());
        when(sseService.hasActiveConnection(userId)).thenReturn(true);
        when(notificationStore.claimPendingNotifications(org.mockito.ArgumentMatchers.eq(userId), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(100))).thenReturn(Flux.empty());
        when(notificationStore.getPendingRecipientIds(UUID.fromString(userId), 100)).thenReturn(Flux.empty());

        service.recoverOnStartup();

        Thread.sleep(300);
        verify(notificationStore).getPendingStats();
        verify(notificationStore, org.mockito.Mockito.timeout(2_000)).claimPendingNotifications(
                org.mockito.ArgumentMatchers.eq(userId), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(100));
    }

    @Test
    void recoverOnStartupProcessesRecipientsBeyondOneBatch() throws Exception {
        List<String> recipients = java.util.stream.IntStream.range(0, 101)
                .mapToObj(index -> UUID.nameUUIDFromBytes(("recipient-" + index).getBytes()).toString())
                .sorted()
                .toList();
        Map<String, Object> stats = Map.of("totalNotifications", 101L, "totalUsers", 101L);
        when(notificationStore.getPendingStats()).thenReturn(Mono.just(stats));
        when(notificationStore.getPendingRecipientIds(null, 100))
                .thenReturn(Flux.fromIterable(recipients.subList(0, 100)));
        when(notificationStore.rebuildProjection(org.mockito.ArgumentMatchers.anyString())).thenReturn(Mono.empty());
        when(sseService.hasActiveConnection(org.mockito.ArgumentMatchers.anyString())).thenReturn(true);
        when(notificationStore.claimPendingNotifications(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(100))).thenReturn(Flux.empty());
        when(notificationStore.getPendingRecipientIds(UUID.fromString(recipients.get(99)), 100))
                .thenReturn(Flux.just(recipients.get(100)));
        when(notificationStore.getPendingRecipientIds(UUID.fromString(recipients.get(100)), 100))
                .thenReturn(Flux.empty());

        service.recoverOnStartup();

        Thread.sleep(500);
        verify(notificationStore).claimPendingNotifications(
                org.mockito.ArgumentMatchers.eq(recipients.get(100)), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(100));
    }

    @Test
    void recoverOnStartupSkipsWhenNoPendingNotifications() throws Exception {
        Map<String, Object> stats = Map.of(
                "totalNotifications", 0L,
                "totalUsers", 0L
        );
        when(notificationStore.getPendingStats()).thenReturn(Mono.just(stats));

        service.recoverOnStartup();

        Thread.sleep(300);
        verify(notificationStore).getPendingStats();
    }

    @Test
    void scheduledRecoverySkipsOfflineRecipientsWithoutClaimingOrExhausting() throws Exception {
        String userId = "00000000-0000-0000-0000-000000000009";
        when(notificationStore.getPendingStats()).thenReturn(Mono.just(Map.of("totalNotifications", 1L)));
        when(notificationStore.getPendingRecipientIds(null, 100)).thenReturn(Flux.just(userId));
        when(notificationStore.getPendingRecipientIds(UUID.fromString(userId), 100)).thenReturn(Flux.empty());
        when(sseService.hasActiveConnection(userId)).thenReturn(false);

        service.recoverDueNotifications();

        Thread.sleep(300);
        verify(notificationStore, org.mockito.Mockito.never()).claimPendingNotifications(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void connectionRecoveryDeliversPendingNotificationAfterSubscriberIsActive() throws Exception {
        String userId = "00000000-0000-0000-0000-000000000010";
        NotificationRequest notification = new NotificationRequest(
                "notification-1", userId, NotificationType.NEW_SEARCH_POSTED, "message", 1L);
        NotificationRecord record = new NotificationRecord(UUID.fromString("00000000-0000-0000-0000-000000000011"),
                UUID.fromString(userId), notification.type().name(),
                "{\"id\":\"notification-1\",\"userId\":\"" + userId + "\",\"type\":\"NEW_SEARCH_POSTED\",\"message\":\"message\",\"sysTime\":1}",
                "PENDING", Instant.now(), Instant.now());
        when(notificationStore.rebuildProjection(userId)).thenReturn(Mono.empty());
        when(notificationStore.claimPendingNotifications(org.mockito.ArgumentMatchers.eq(userId), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(100)))
                .thenReturn(Flux.just(new HybridNotificationStore.ClaimedNotification(record.publicId(), notification)));
        when(sseService.sendViaSSE(userId, notification)).thenReturn(true);
        when(notificationStore.recordAttempt(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("SSE"),
                org.mockito.ArgumentMatchers.eq("WRITTEN"), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(Mono.empty());
        when(notificationStore.acknowledgeDelivery(org.mockito.ArgumentMatchers.eq(record.publicId()),
                org.mockito.ArgumentMatchers.anyString())).thenReturn(Mono.just(true));

        service.deliverPendingOnConnect(userId).block();

        verify(sseService).sendViaSSE(userId, notification);
        verify(notificationStore).acknowledgeDelivery(org.mockito.ArgumentMatchers.eq(record.publicId()),
                org.mockito.ArgumentMatchers.anyString());
    }
}
