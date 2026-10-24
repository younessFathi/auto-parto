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
        when(notificationStore.validateReplayCursor(userId.toString(), cursor)).thenReturn(reactor.core.publisher.Mono.just(true));
        when(notificationStore.replayNotifications(userId.toString(), cursor, 100))
                .thenReturn(Flux.just(new HybridNotificationStore.ClaimedNotification(persistedEventId, notification)));

        VersionedNotificationController controller = new VersionedNotificationController(connectionService, notificationStore);

        StepVerifier.create(controller.stream(CanonicalTestAuthentication.buyer(userId.toString()), cursor.toString())
                        .flatMapMany(response -> response.getBody()))
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

        assertThat(controller.stream(CanonicalTestAuthentication.buyer(userId.toString()), null).block()
                .getStatusCode().value()).isEqualTo(429);
    }

    @Test
    void principalOwnsStreamAndNoPathIdentityCanBeSubstituted() {
        UUID userId = UUID.randomUUID();
        UUID cursor = UUID.randomUUID();
        Sinks.Many<SseNotificationEvent> sink = Sinks.many().unicast().onBackpressureBuffer();
        when(connectionService.registerConnection(userId.toString())).thenReturn(sink);
        when(notificationStore.validateReplayCursor(userId.toString(), cursor)).thenReturn(reactor.core.publisher.Mono.just(true));
        when(notificationStore.replayNotifications(eq(userId.toString()), eq(cursor), eq(100)))
                .thenReturn(Flux.empty());

        VersionedNotificationController controller = new VersionedNotificationController(connectionService, notificationStore);
        StepVerifier.create(controller.stream(CanonicalTestAuthentication.buyer(userId.toString()), cursor.toString())
                        .flatMapMany(response -> response.getBody()))
                .thenRequest(1)
                .thenAwait(java.time.Duration.ofMillis(50))
                .thenCancel()
                .verify();

        verify(connectionService).registerConnection(userId.toString());
        verify(notificationStore).replayNotifications(userId.toString(), cursor, 100);
    }

    @Test
    void foreignOrMissingCursorIsRejectedBeforeConnectionRegistration() {
        UUID userId = UUID.randomUUID();
        UUID cursor = UUID.randomUUID();
        when(notificationStore.validateReplayCursor(userId.toString(), cursor)).thenReturn(reactor.core.publisher.Mono.just(false));
        VersionedNotificationController controller = new VersionedNotificationController(connectionService, notificationStore);

        StepVerifier.create(controller.stream(CanonicalTestAuthentication.buyer(userId.toString()), cursor.toString()))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ResponseStatusException.class)
                        .hasMessageContaining("does not identify a notification"))
                .verify();
        verify(connectionService, never()).registerConnection(userId.toString());
    }

    @Test
    void streamCompletesAndCleansUpWhenTheRegisteredSinkCompletes() {
        UUID userId = UUID.randomUUID();
        Sinks.Many<SseNotificationEvent> sink = Sinks.many().unicast().onBackpressureBuffer();
        when(connectionService.registerConnection(userId.toString())).thenReturn(sink);
        VersionedNotificationController controller = new VersionedNotificationController(connectionService, notificationStore);

        StepVerifier.create(controller.stream(CanonicalTestAuthentication.buyer(userId.toString()), null)
                        .flatMapMany(response -> response.getBody()))
                .thenRequest(1)
                .then(() -> sink.tryEmitComplete())
                .verifyComplete();

        verify(connectionService).unregisterConnection(userId.toString(), sink);
    }

    @Test
    void cancellationCleansUpMatchingSinkAndStopsHeartbeat() {
        UUID userId = UUID.randomUUID();
        Sinks.Many<SseNotificationEvent> sink = Sinks.many().unicast().onBackpressureBuffer();
        when(connectionService.registerConnection(userId.toString())).thenReturn(sink);
        VersionedNotificationController controller = new VersionedNotificationController(connectionService, notificationStore);
        Flux<org.springframework.http.codec.ServerSentEvent<NotificationRequest>> stream =
                controller.stream(CanonicalTestAuthentication.buyer(userId.toString()), null).block().getBody();

        StepVerifier.create(stream)
                .thenRequest(1)
                .thenAwait(java.time.Duration.ofMillis(50))
                .thenCancel()
                .verify();

        verify(connectionService).unregisterConnection(userId.toString(), sink);
    }

    @Test
    void replacementCompletesOldStreamWithoutCleaningUpReplacement() {
        UUID userId = UUID.randomUUID();
        Sinks.Many<SseNotificationEvent> oldSink = Sinks.many().unicast().onBackpressureBuffer();
        Sinks.Many<SseNotificationEvent> replacementSink = Sinks.many().unicast().onBackpressureBuffer();
        when(connectionService.registerConnection(userId.toString())).thenReturn(oldSink, replacementSink);
        VersionedNotificationController controller = new VersionedNotificationController(connectionService, notificationStore);
        Flux<org.springframework.http.codec.ServerSentEvent<NotificationRequest>> oldStream =
                controller.stream(CanonicalTestAuthentication.buyer(userId.toString()), null).block().getBody();

        StepVerifier.create(oldStream)
                .thenRequest(1)
                .then(() -> oldSink.tryEmitComplete())
                .verifyComplete();
        controller.stream(CanonicalTestAuthentication.buyer(userId.toString()), null).block();

        verify(connectionService).unregisterConnection(userId.toString(), oldSink);
        verify(connectionService, never()).unregisterConnection(userId.toString(), replacementSink);
    }

    private static NotificationRequest notification(UUID userId) {
        return new NotificationRequest("payload-id", userId.toString(), NotificationType.NEW_SEARCH_POSTED,
                "safe test message", 1L);
    }
}
