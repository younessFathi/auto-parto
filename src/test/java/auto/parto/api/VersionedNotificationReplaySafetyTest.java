package auto.parto.api;

import auto.parto.dto.NotificationRequest;
import auto.parto.enums.NotificationType;
import auto.parto.persistence.NotificationRecord;
import auto.parto.persistence.NotificationRepository;
import auto.parto.service.notification.HybridNotificationStore;
import auto.parto.service.notification.NotificationPreferenceFilter;
import auto.parto.service.notification.RedisNotificationStore;
import auto.parto.service.notification.ResilientNotificationService;
import auto.parto.service.notification.SseNotificationEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** Exercises the real replay preference pipeline together with the live SSE lifecycle. */
@ExtendWith(MockitoExtension.class)
class VersionedNotificationReplaySafetyTest {
    @Mock private ResilientNotificationService connections;
    @Mock private NotificationRepository repository;
    @Mock private ObjectProvider<NotificationRepository> repositories;
    @Mock private RedisNotificationStore redis;
    @Mock private NotificationPreferenceFilter preferences;
    private final UUID user = UUID.randomUUID();
    private final UUID cursor = UUID.randomUUID();
    private final UUID firstId = UUID.randomUUID();
    private final UUID secondId = UUID.randomUUID();
    private final UUID liveId = UUID.randomUUID();

    @Test
    void detectedPreferenceFailureEmitsNeitherLaterReplayNorLiveAndReconnectRecoversOriginalCursor() throws Exception {
        VersionedNotificationController controller = controller();
        Sinks.Many<SseNotificationEvent> oldSink = liveSink(), newSink = liveSink();
        when(connections.registerConnection(user.toString())).thenReturn(oldSink, newSink);
        replayPair();
        Sinks.One<Boolean> firstLookup = Sinks.one();
        when(preferences.canSend(user.toString(), "push", NotificationType.NEW_SEARCH_POSTED.name()))
                .thenReturn(firstLookup.asMono(), Mono.just(true));

        StepVerifier.create(stream(controller))
                .then(() -> firstLookup.tryEmitError(new IllegalStateException("lookup unavailable")))
                .expectError().verify(Duration.ofSeconds(1));
        assertThat(oldSink.tryEmitNext(liveEvent())).isNotEqualTo(Sinks.EmitResult.OK);
        verify(connections).unregisterConnection(user.toString(), oldSink);

        StepVerifier.create(stream(controller))
                .expectNextMatches(event -> firstId.toString().equals(event.id()))
                .expectNextMatches(event -> secondId.toString().equals(event.id()))
                .thenCancel().verify(Duration.ofSeconds(1));
        verify(repository, times(2)).findForRecipientAfter(user, cursor, 100);
        verify(connections).unregisterConnection(user.toString(), newSink);
    }

    @Test
    void liveContentWaitsForDelayedReplayAndIsDiscardedIfThatLookupFails() throws Exception {
        VersionedNotificationController controller = controller();
        Sinks.Many<SseNotificationEvent> sink = liveSink();
        when(connections.registerConnection(user.toString())).thenReturn(sink);
        replayPair();
        Sinks.One<Boolean> firstLookup = Sinks.one();
        when(preferences.canSend(user.toString(), "push", NotificationType.NEW_SEARCH_POSTED.name()))
                .thenReturn(firstLookup.asMono());

        StepVerifier.create(stream(controller))
                .then(() -> assertThat(sink.tryEmitNext(liveEvent())).isEqualTo(Sinks.EmitResult.OK))
                .then(() -> firstLookup.tryEmitError(new IllegalStateException("lookup unavailable")))
                .expectError().verify(Duration.ofSeconds(1));
        verify(connections).unregisterConnection(user.toString(), sink);
    }

    @Test
    void delayedSuccessfulReplayIsEmittedBeforeBufferedLiveContent() throws Exception {
        VersionedNotificationController controller = controller();
        Sinks.Many<SseNotificationEvent> sink = liveSink();
        when(connections.registerConnection(user.toString())).thenReturn(sink);
        replayPair();
        Sinks.One<Boolean> firstLookup = Sinks.one();
        when(preferences.canSend(user.toString(), "push", NotificationType.NEW_SEARCH_POSTED.name()))
                .thenReturn(firstLookup.asMono(), Mono.just(true));

        StepVerifier.create(stream(controller))
                .then(() -> assertThat(sink.tryEmitNext(liveEvent())).isEqualTo(Sinks.EmitResult.OK))
                .then(() -> firstLookup.tryEmitValue(true))
                .expectNextMatches(event -> firstId.toString().equals(event.id()))
                .expectNextMatches(event -> secondId.toString().equals(event.id()))
                .expectNextMatches(event -> liveId.toString().equals(event.id()))
                .thenCancel().verify(Duration.ofSeconds(1));
    }

    @Test
    void liveBufferOverflowTerminatesAndCancelsUnresolvedReplay() {
        VersionedNotificationController controller = controller();
        Sinks.Many<SseNotificationEvent> sink = liveSink();
        when(connections.registerConnection(user.toString())).thenReturn(sink);
        AtomicBoolean replaySubscribed = new AtomicBoolean();
        AtomicBoolean replayCancelled = new AtomicBoolean();
        when(repository.findForRecipientAfter(user, cursor, 100))
                .thenReturn(Flux.<NotificationRecord>never()
                        .doOnCancel(() -> replayCancelled.set(true))
                        .doOnRequest(ignored -> {
                            if (!replaySubscribed.compareAndSet(false, true)) return;
                            // Demand proves downstream installed the replay subscription;
                            // an earlier overflow may prevent recovery from starting at all.
                            for (int i = 0; i < 102; i++) {
                                assertThat(sink.tryEmitNext(liveEvent())).isEqualTo(Sinks.EmitResult.OK);
                            }
                        }));

        StepVerifier.create(stream(controller))
                .expectErrorMatches(reactor.core.Exceptions::isOverflow).verify(Duration.ofSeconds(1));
        assertThat(replaySubscribed).isTrue();
        assertThat(replayCancelled).isTrue();
        verify(connections).unregisterConnection(user.toString(), sink);
        assertThat(sink.currentSubscriberCount()).isZero();
    }

    @Test
    void liveSourceFailureCancelsActiveReplayAndClosesConnection() {
        VersionedNotificationController controller = controller();
        Sinks.Many<SseNotificationEvent> sink = liveSink();
        when(connections.registerConnection(user.toString())).thenReturn(sink);
        AtomicBoolean replayRequested = new AtomicBoolean(), replayCancelled = new AtomicBoolean();
        when(repository.findForRecipientAfter(user, cursor, 100)).thenReturn(Flux.<NotificationRecord>never()
                .doOnCancel(() -> replayCancelled.set(true))
                .doOnRequest(ignored -> {
                    if (replayRequested.compareAndSet(false, true)) {
                        sink.tryEmitError(new IllegalStateException("live source unavailable"));
                    }
                }));

        StepVerifier.create(stream(controller)).expectError().verify(Duration.ofSeconds(1));
        assertThat(replayRequested).isTrue();
        assertThat(replayCancelled).isTrue();
        assertThat(sink.currentSubscriberCount()).isZero();
        verify(connections).unregisterConnection(user.toString(), sink);
    }

    @Test
    void cancellationDropsBufferedLiveAndCancelsPreferenceLookup() throws Exception {
        VersionedNotificationController controller = controller();
        Sinks.Many<SseNotificationEvent> sink = liveSink();
        when(connections.registerConnection(user.toString())).thenReturn(sink);
        replayPair();
        AtomicBoolean lookupCancelled = new AtomicBoolean();
        when(preferences.canSend(user.toString(), "push", NotificationType.NEW_SEARCH_POSTED.name()))
                .thenReturn(Mono.<Boolean>never().doOnCancel(() -> lookupCancelled.set(true)));

        StepVerifier.create(stream(controller))
                .then(() -> sink.tryEmitNext(liveEvent()))
                .thenCancel().verify(Duration.ofSeconds(1));
        assertThat(lookupCancelled).isTrue();
        assertThat(sink.currentSubscriberCount()).isZero();
        verify(connections).unregisterConnection(user.toString(), sink);
    }

    @Test
    void replacementCompletesOldStreamWithoutDrainingHeldContentOrUnregisteringNewConnection() throws Exception {
        VersionedNotificationController controller = controller();
        Sinks.Many<SseNotificationEvent> oldSink = liveSink(), newSink = liveSink();
        when(connections.registerConnection(user.toString())).thenReturn(oldSink, newSink);
        replayPair();
        AtomicBoolean lookupCancelled = new AtomicBoolean();
        when(preferences.canSend(user.toString(), "push", NotificationType.NEW_SEARCH_POSTED.name()))
                .thenReturn(Mono.<Boolean>never().doOnCancel(() -> lookupCancelled.set(true)));

        StepVerifier.create(stream(controller))
                .then(() -> oldSink.tryEmitNext(liveEvent()))
                .then(() -> oldSink.tryEmitComplete())
                .expectComplete().verify(Duration.ofSeconds(1));
        assertThat(lookupCancelled).isTrue();
        verify(connections).unregisterConnection(user.toString(), oldSink);

        StepVerifier.create(controller.stream(CanonicalTestAuthentication.buyer(user.toString()), null)
                        .flatMapMany(response -> response.getBody()))
                .then(() -> newSink.tryEmitNext(liveEvent()))
                .expectNextMatches(event -> liveId.toString().equals(event.id()))
                .thenCancel().verify(Duration.ofSeconds(1));
        verify(connections).unregisterConnection(user.toString(), newSink);
    }

    @Test
    void contentFreeHeartbeatContinuesWhileReplayIsUnresolvedAndStopsOnCancellation() {
        VersionedNotificationController controller = controller();
        Sinks.Many<SseNotificationEvent> sink = liveSink();
        when(connections.registerConnection(user.toString())).thenReturn(sink);
        AtomicBoolean replayCancelled = new AtomicBoolean();
        when(repository.findForRecipientAfter(user, cursor, 100))
                .thenReturn(Flux.<NotificationRecord>never().doOnCancel(() -> replayCancelled.set(true)));

        StepVerifier.withVirtualTime(() -> stream(controller))
                .thenAwait(Duration.ofSeconds(30))
                .expectNextMatches(event -> "heartbeat".equals(event.comment()) && event.id() == null && event.data() == null)
                .thenCancel().verify(Duration.ofSeconds(1));
        assertThat(replayCancelled).isTrue();
        verify(connections).unregisterConnection(user.toString(), sink);
    }

    @Test
    void downstreamDemandCannotReleaseLiveAheadOfQueuedReplay() throws Exception {
        for (long initialDemand : new long[]{0, 1, 3}) {
            VersionedNotificationController controller = controller();
            Sinks.Many<SseNotificationEvent> sink = liveSink();
            when(connections.registerConnection(user.toString())).thenReturn(sink);
            UUID thirdId = UUID.randomUUID();
            ObjectMapper mapper = new ObjectMapper();
            when(repository.findForRecipientAfter(user, cursor, 100)).thenReturn(Flux.just(
                    new NotificationRecord(firstId, user, "NEW_SEARCH_POSTED", mapper.writeValueAsString(notification("first")), "DISPATCHED", Instant.EPOCH, Instant.EPOCH),
                    new NotificationRecord(secondId, user, "NEW_SEARCH_POSTED", mapper.writeValueAsString(notification("second")), "DISPATCHED", Instant.EPOCH, Instant.EPOCH),
                    new NotificationRecord(thirdId, user, "NEW_SEARCH_POSTED", mapper.writeValueAsString(notification("third")), "DISPATCHED", Instant.EPOCH, Instant.EPOCH)));
            Sinks.One<Boolean> lookup = Sinks.one();
            when(preferences.canSend(user.toString(), "push", "NEW_SEARCH_POSTED"))
                    .thenReturn(lookup.asMono(), Mono.just(true));
            java.util.List<String> received = new java.util.ArrayList<>();
            reactor.core.publisher.BaseSubscriber<ServerSentEvent<NotificationRequest>> subscriber =
                    new reactor.core.publisher.BaseSubscriber<>() {
                        @Override protected void hookOnSubscribe(org.reactivestreams.Subscription subscription) {
                            if (initialDemand > 0) request(initialDemand);
                        }
                        @Override protected void hookOnNext(ServerSentEvent<NotificationRequest> event) {
                            received.add(event.id());
                            if (liveId.toString().equals(event.id())) cancel();
                        }
                    };
            stream(controller).subscribe(subscriber);
            // Drive demand only after subscribe and all synchronous prefetch work return.
            assertThat(sink.tryEmitNext(liveEvent())).isEqualTo(Sinks.EmitResult.OK);
            lookup.tryEmitValue(true);
            for (int i = 0; i < 4; i++) subscriber.request(1);
            subscriber.cancel();
            assertThat(received).containsExactly(firstId.toString(), secondId.toString(), thirdId.toString(), liveId.toString());
        }
    }

    private VersionedNotificationController controller() {
        when(repositories.getIfAvailable()).thenReturn(repository);
        when(repository.cursorBelongsToRecipient(user, cursor)).thenReturn(Mono.just(true));
        return new VersionedNotificationController(connections,
                new HybridNotificationStore(redis, repositories, new ObjectMapper(), preferences));
    }

    private Flux<ServerSentEvent<NotificationRequest>> stream(VersionedNotificationController controller) {
        return controller.stream(CanonicalTestAuthentication.buyer(user.toString()), cursor.toString())
                .flatMapMany(response -> response.getBody());
    }

    private void replayPair() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        NotificationRequest first = notification("first"), second = notification("second");
        when(repository.findForRecipientAfter(user, cursor, 100)).thenReturn(Flux.just(
                new NotificationRecord(firstId, user, first.type().name(), mapper.writeValueAsString(first),
                        "DISPATCHED", Instant.EPOCH, Instant.EPOCH),
                new NotificationRecord(secondId, user, second.type().name(), mapper.writeValueAsString(second),
                        "DISPATCHED", Instant.EPOCH, Instant.EPOCH)));
    }

    private NotificationRequest notification(String id) {
        return new NotificationRequest(id, user.toString(), NotificationType.NEW_SEARCH_POSTED, "safe", 1L);
    }

    private SseNotificationEvent liveEvent() { return new SseNotificationEvent(liveId, notification("live")); }
    private Sinks.Many<SseNotificationEvent> liveSink() {
        return Sinks.many().unicast().onBackpressureBuffer(new ArrayBlockingQueue<>(256));
    }
}
