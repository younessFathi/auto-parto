package auto.parto.api;

import auto.parto.service.notification.PendingNotificationService;
import auto.parto.service.notification.ResilientNotificationService;
import auto.parto.service.notification.ResilientPendingNotificationService;
import auto.parto.service.notification.SseNotificationEvent;
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
        Sinks.Many<SseNotificationEvent> userSink =
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
        verify(resilientNotificationService).unregisterConnection(userId, userSink);
    }

    @Test
    void subscribeToUser_completesAndCleansUpWhenTheRegisteredSinkIsReplaced() {
        String userId = "user-1";
        Sinks.Many<SseNotificationEvent> oldSink =
                Sinks.many().unicast().onBackpressureBuffer();
        when(resilientNotificationService.registerConnection(userId)).thenReturn(oldSink);
        when(resilientPendingNotificationService.deliverPendingOnConnect(userId))
                .thenReturn(Mono.just(0));

        NotificationController controller = new NotificationController(
                resilientNotificationService, pendingNotificationService,
                resilientPendingNotificationService);

        StepVerifier.create(controller.subscribeToUser(userId, CanonicalTestAuthentication.buyer(userId)))
                .thenRequest(1)
                .then(() -> oldSink.tryEmitComplete())
                .verifyComplete();

        verify(resilientNotificationService).unregisterConnection(userId, oldSink);
    }

    @Test
    void subscribeToUser_keepsStreamOpenAfterRecoveryUntilSinkCompletes() {
        String userId = "user-1";
        Sinks.Many<SseNotificationEvent> userSink =
                Sinks.many().unicast().onBackpressureBuffer();
        PublisherProbe<Integer> recovery = PublisherProbe.of(Mono.just(0));
        when(resilientNotificationService.registerConnection(userId)).thenReturn(userSink);
        when(resilientPendingNotificationService.deliverPendingOnConnect(userId))
                .thenReturn(recovery.mono());

        NotificationController controller = new NotificationController(
                resilientNotificationService, pendingNotificationService,
                resilientPendingNotificationService);

        StepVerifier.create(controller.subscribeToUser(userId, CanonicalTestAuthentication.buyer(userId)))
                .thenRequest(1)
                .thenAwait(Duration.ofMillis(50))
                .then(() -> assertThat(recovery.wasSubscribed()).isTrue())
                .then(() -> userSink.tryEmitComplete())
                .verifyComplete();

        verify(resilientNotificationService).unregisterConnection(userId, userSink);
    }

    @Test
    void subscribeToUser_cancelsRecoveryWhenOldSinkIsReplacedAndKeepsReplacement() {
        String userId = "user-1";
        Sinks.Many<SseNotificationEvent> oldSink =
                Sinks.many().unicast().onBackpressureBuffer();
        Sinks.Many<SseNotificationEvent> replacementSink =
                Sinks.many().unicast().onBackpressureBuffer();
        PublisherProbe<Integer> recovery = PublisherProbe.of(Mono.never());
        when(resilientNotificationService.registerConnection(userId))
                .thenReturn(oldSink, replacementSink);
        when(resilientPendingNotificationService.deliverPendingOnConnect(userId))
                .thenReturn(recovery.mono(), Mono.just(0));

        NotificationController controller = new NotificationController(
                resilientNotificationService, pendingNotificationService,
                resilientPendingNotificationService);
        StepVerifier.create(controller.subscribeToUser(userId, CanonicalTestAuthentication.buyer(userId)))
                .thenRequest(1)
                .thenAwait(Duration.ofMillis(50))
                .then(() -> controller.subscribeToUser(userId, CanonicalTestAuthentication.buyer(userId)))
                .then(() -> oldSink.tryEmitComplete())
                .verifyComplete();

        assertThat(recovery.wasSubscribed()).isTrue();
        assertThat(recovery.wasCancelled()).isTrue();
        verify(resilientNotificationService, org.mockito.Mockito.times(2)).registerConnection(userId);
        verify(resilientNotificationService).unregisterConnection(userId, oldSink);
        verify(resilientNotificationService, org.mockito.Mockito.never())
                .unregisterConnection(userId, replacementSink);
    }

    @Test
    void subscribeToUser_cleansUpSafelyWhenRecoveryFails() {
        String userId = "user-1";
        Sinks.Many<SseNotificationEvent> oldSink =
                Sinks.many().unicast().onBackpressureBuffer();
        Sinks.Many<SseNotificationEvent> replacementSink =
                Sinks.many().unicast().onBackpressureBuffer();
        when(resilientNotificationService.registerConnection(userId))
                .thenReturn(oldSink, replacementSink);
        when(resilientPendingNotificationService.deliverPendingOnConnect(userId))
                .thenReturn(Mono.error(new IllegalStateException("recovery failed")), Mono.just(0));

        NotificationController controller = new NotificationController(
                resilientNotificationService, pendingNotificationService,
                resilientPendingNotificationService);

        StepVerifier.create(controller.subscribeToUser(userId, CanonicalTestAuthentication.buyer(userId)))
                .thenRequest(1)
                .expectError(IllegalStateException.class)
                .verify();
        controller.subscribeToUser(userId, CanonicalTestAuthentication.buyer(userId));

        verify(resilientNotificationService).unregisterConnection(userId, oldSink);
        verify(resilientNotificationService, org.mockito.Mockito.never())
                .unregisterConnection(userId, replacementSink);
    }
}
