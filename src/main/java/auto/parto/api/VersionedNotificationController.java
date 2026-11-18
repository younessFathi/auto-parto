package auto.parto.api;

import auto.parto.dto.NotificationRequest;
import auto.parto.security.CanonicalPrincipal;
import auto.parto.security.CanonicalSecurity;
import auto.parto.service.notification.HybridNotificationStore;
import auto.parto.service.notification.ResilientNotificationService;
import auto.parto.service.notification.SseNotificationEvent;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.Exceptions;
import reactor.core.publisher.BufferOverflowStrategy;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.UUID;

/** Canonical, durable notification stream. */
@RestController
@RequestMapping("/api/v1/notifications")
public class VersionedNotificationController {

    private static final int REPLAY_LIMIT = 100;
    private static final int LIVE_REPLAY_BUFFER_LIMIT = 100;
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(30);

    private final ResilientNotificationService connectionService;
    private final HybridNotificationStore notificationStore;

    public VersionedNotificationController(ResilientNotificationService connectionService,
                                           HybridNotificationStore notificationStore) {
        this.connectionService = connectionService;
        this.notificationStore = notificationStore;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("isAuthenticated()")
    public Mono<ResponseEntity<Flux<ServerSentEvent<NotificationRequest>>>> stream(
            Authentication authentication,
            @RequestHeader(name = "Last-Event-ID", required = false) String lastEventId) {
        CanonicalPrincipal principal = CanonicalSecurity.requirePrincipal(authentication);
        UUID userId = parseUuid(principal.getId(), "authenticated principal");
        UUID cursor = parseOptionalUuid(lastEventId);
        Mono<Boolean> cursorCheck = cursor == null
                ? Mono.just(true)
                : notificationStore.validateReplayCursor(principal.getId(), cursor);
        return cursorCheck.flatMap(valid -> {
            if (!valid) {
                return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Last-Event-ID does not identify a notification for this user"));
            }
            Sinks.Many<SseNotificationEvent> sink = connectionService.registerConnection(principal.getId());
            if (sink == null) {
                return Mono.just(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.TEXT_EVENT_STREAM)
                        .body(Flux.<ServerSentEvent<NotificationRequest>>empty()));
            }
            return Mono.just(ResponseEntity.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .body(buildStream(principal.getId(), cursor, sink)));
        });
    }

    private Flux<ServerSentEvent<NotificationRequest>> buildStream(String userId, UUID cursor,
                                                                     Sinks.Many<SseNotificationEvent> sink) {
        return Flux.defer(() -> sink.asFlux().publish(shared -> {
                    Sinks.One<Void> replayFinished = Sinks.one();
                    Sinks.One<Void> streamFailure = Sinks.one();
                    Mono<Void> sinkClosed = shared.ignoreElements()
                            .doOnError(streamFailure::tryEmitError)
                            .onErrorResume(error -> Mono.never())
                            .then();
                    // Hold one item and at most 100 queued live events until replay succeeds.
                    // Overflow fails the whole stream rather than advancing past unresolved replay.
                    Flux<ServerSentEvent<NotificationRequest>> live = shared
                            .onBackpressureBuffer(LIVE_REPLAY_BUFFER_LIMIT,
                                    ignored -> streamFailure.tryEmitError(Exceptions.failWithOverflow(
                                            "Live notification replay buffer exceeded")),
                                    BufferOverflowStrategy.ERROR)
                            .concatMap(event -> replayFinished.asMono().thenReturn(event), 0)
                            .map(VersionedNotificationController::toSse);
                    Flux<ServerSentEvent<NotificationRequest>> replay = (cursor == null
                            ? Flux.<ServerSentEvent<NotificationRequest>>empty()
                            : notificationStore.replayNotifications(userId, cursor, REPLAY_LIMIT)
                                .map(event -> toSse(new SseNotificationEvent(event.publicId(), event.notification()))))
                            .doOnComplete(() -> replayFinished.tryEmitEmpty());
                    // Register live before starting replay; replay errors cancel live and heartbeats.
                    // Close/replacement cancels the entire chain without releasing held content.
                    // Fail through a merge lane so all active sources are cancelled immediately;
                    // a buffered error can otherwise wait behind the unresolved replay gate.
                    Flux<ServerSentEvent<NotificationRequest>> failure = streamFailure.asMono()
                            .thenMany(Flux.empty());
                    return Flux.merge(live, replay, heartbeat(), failure).takeUntilOther(sinkClosed);
                }))
                .doFinally(signal -> connectionService.unregisterConnection(userId, sink));
    }

    private static UUID parseOptionalUuid(String value) {
        if (value == null || value.isBlank()) return null;
        return parseUuid(value, "Last-Event-ID");
    }

    private static UUID parseUuid(String value, String field) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must be a UUID");
        }
    }

    private static ServerSentEvent<NotificationRequest> toSse(SseNotificationEvent event) {
        NotificationRequest notification = event.notification();
        return ServerSentEvent.<NotificationRequest>builder()
                .id(event.eventId().toString())
                .event(notification.type().name())
                .data(notification)
                .retry(Duration.ofSeconds(10))
                .build();
    }

    private static Flux<ServerSentEvent<NotificationRequest>> heartbeat() {
        return Flux.interval(HEARTBEAT_INTERVAL)
                .map(tick -> ServerSentEvent.<NotificationRequest>builder().comment("heartbeat").build())
                .onBackpressureDrop();
    }
}
