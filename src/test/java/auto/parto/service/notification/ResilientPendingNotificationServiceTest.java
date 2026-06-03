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
        Map<String, Object> stats = Map.of(
                "totalNotifications", 2L,
                "totalUsers", 1L,
                "usersWithPending", List.of("user-1")
        );
        when(notificationStore.getPendingStats()).thenReturn(Mono.just(stats));
        when(notificationStore.getPendingNotifications("user-1")).thenReturn(Flux.empty());

        service.recoverOnStartup();

        Thread.sleep(300);
        verify(notificationStore).getPendingStats();
        verify(notificationStore).getPendingNotifications("user-1");
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
