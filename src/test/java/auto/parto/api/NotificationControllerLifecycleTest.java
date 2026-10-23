package auto.parto.api;

import auto.parto.service.notification.PendingNotificationService;
import auto.parto.service.notification.ResilientNotificationService;
import auto.parto.service.notification.ResilientPendingNotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;
import reactor.test.publisher.PublisherProbe;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationControllerLifecycleTest {

    @Mock
    private ResilientNotificationService resilientNotificationService;
    @Mock
    private PendingNotificationService pendingNotificationService;
    @Mock
    private ResilientPendingNotificationService resilientPendingNotificationService;

    @Test
    void subscribeToUser_cancelsPendingRecoveryWithTheSseConnection() {
        String userId = "user-1";
        Sinks.Many<auto.parto.dto.NotificationRequest> userSink =
                Sinks.many().unicast().onBackpressureBuffer();
        PublisherProbe<Integer> recovery = PublisherProbe.of(Mono.never());
        when(resilientNotificationService.registerConnection(userId)).thenReturn(userSink);
        when(resilientPendingNotificationService.deliverPendingOnConnect(userId))
                .thenReturn(recovery.mono());

        NotificationController controller = new NotificationController(
                resilientNotificationService, pendingNotificationService,
                resilientPendingNotificationService);

        StepVerifier.create(controller.subscribeToUser(userId, CanonicalTestAuthentication.buyer(userId)))
                .thenRequest(1)
                .thenAwait(Duration.ofMillis(50))
                .thenCancel()
                .verify();

        verify(resilientPendingNotificationService).deliverPendingOnConnect(userId);
        assertThat(recovery.wasSubscribed()).isTrue();
        assertThat(recovery.wasCancelled()).isTrue();
    }
}
