package auto.parto.api;

import auto.parto.dto.NotificationRequest;
import auto.parto.enums.NotificationType;
import auto.parto.service.notification.HybridNotificationStore;
import auto.parto.service.notification.ResilientNotificationService;
import auto.parto.service.notification.SseNotificationEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VersionedNotificationControllerTest {

    @Mock
    private ResilientNotificationService connectionService;
    @Mock
    private HybridNotificationStore notificationStore;

    @Test
    void replayUsesPersistedEventIdAndCanonicalPrincipal() {
        UUID userId = UUID.randomUUID();
        UUID cursor = UUID.randomUUID();
        UUID persistedEventId = UUID.randomUUID();
        NotificationRequest notification = notification(userId);
        Sinks.Many<SseNotificationEvent> sink = Sinks.many().unicast().onBackpressureBuffer();
        when(connectionService.registerConnection(userId.toString())).thenReturn(sink);
        when(notificationStore.replayNotifications(userId.toString(), cursor, 100))
                .thenReturn(Flux.just(new HybridNotificationStore.ClaimedNotification(persistedEventId, notification)));

        VersionedNotificationController controller = new VersionedNotificationController(connectionService, notificationStore);

        StepVerifier.create(controller.stream(CanonicalTestAuthentication.buyer(userId.toString()), cursor.toString()))
                .thenRequest(1)
                .expectNextMatches(event -> persistedEventId.toString().equals(event.id())
                        && event.data().equals(notification))
                .thenCancel()
                .verify();

        verify(notificationStore).replayNotifications(userId.toString(), cursor, 100);
        verify(connectionService).unregisterConnection(userId.toString(), sink);
    }

    @Test
    void malformedLastEventIdIsRejectedBeforeConnectionRegistration() {
        UUID userId = UUID.randomUUID();
        VersionedNotificationController controller = new VersionedNotificationController(connectionService, notificationStore);

        assertThatThrownBy(() -> controller.stream(CanonicalTestAuthentication.buyer(userId.toString()), "bad-cursor"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Last-Event-ID must be a UUID");
        verify(connectionService, never()).registerConnection(userId.toString());
    }

    @Test
    void connectionCapReturnsControlledTooManyRequestsError() {
        UUID userId = UUID.randomUUID();
        when(connectionService.registerConnection(userId.toString())).thenReturn(null);
        VersionedNotificationController controller = new VersionedNotificationController(connectionService, notificationStore);

        StepVerifier.create(controller.stream(CanonicalTestAuthentication.buyer(userId.toString()), null))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ResponseStatusException.class)
                        .extracting("statusCode.value")
                        .isEqualTo(429))
                .verify();
    }

    @Test
    void principalOwnsStreamAndNoPathIdentityCanBeSubstituted() {
        UUID userId = UUID.randomUUID();
        UUID cursor = UUID.randomUUID();
        Sinks.Many<SseNotificationEvent> sink = Sinks.many().unicast().onBackpressureBuffer();
        when(connectionService.registerConnection(userId.toString())).thenReturn(sink);
        when(notificationStore.replayNotifications(eq(userId.toString()), eq(cursor), eq(100)))
                .thenReturn(Flux.empty());

        VersionedNotificationController controller = new VersionedNotificationController(connectionService, notificationStore);
        StepVerifier.create(controller.stream(CanonicalTestAuthentication.buyer(userId.toString()), cursor.toString()))
                .thenRequest(1)
                .thenAwait(java.time.Duration.ofMillis(50))
                .thenCancel()
                .verify();

        verify(connectionService).registerConnection(userId.toString());
        verify(notificationStore).replayNotifications(userId.toString(), cursor, 100);
    }

    private static NotificationRequest notification(UUID userId) {
        return new NotificationRequest("payload-id", userId.toString(), NotificationType.NEW_SEARCH_POSTED,
                "safe test message", 1L);
    }
}
