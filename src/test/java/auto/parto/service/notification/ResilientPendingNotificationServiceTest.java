package auto.parto.service.notification;

import auto.parto.service.BasicCircuitBreaker;
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
        when(notificationStore.getPendingRecipientIds(UUID.fromString(userId), 100)).thenReturn(Flux.empty());
        when(notificationStore.getPendingNotifications(userId)).thenReturn(Flux.empty());

        service.recoverOnStartup();

        Thread.sleep(300);
        verify(notificationStore).getPendingStats();
        verify(notificationStore, org.mockito.Mockito.timeout(2_000)).getPendingNotifications(userId);
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
        when(notificationStore.getPendingRecipientIds(UUID.fromString(recipients.get(99)), 100))
                .thenReturn(Flux.just(recipients.get(100)));
        when(notificationStore.getPendingRecipientIds(UUID.fromString(recipients.get(100)), 100))
                .thenReturn(Flux.empty());
        when(notificationStore.getPendingNotifications(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Flux.empty());

        service.recoverOnStartup();

        Thread.sleep(500);
        verify(notificationStore).getPendingNotifications(recipients.get(100));
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
}
