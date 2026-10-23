package auto.parto.api;

import auto.parto.dto.NotificationRequest;
import auto.parto.security.CanonicalPrincipal;
import auto.parto.security.CanonicalSecurity;
import auto.parto.service.notification.HybridNotificationStore;
import auto.parto.service.notification.ResilientNotificationService;
import auto.parto.service.notification.SseNotificationEvent;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
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
    public Flux<ServerSentEvent<NotificationRequest>> stream(
            Authentication authentication,
            @RequestHeader(name = "Last-Event-ID", required = false) String lastEventId) {
        CanonicalPrincipal principal = CanonicalSecurity.requirePrincipal(authentication);
        UUID userId = parseUuid(principal.getId(), "authenticated principal");
        UUID cursor = parseOptionalUuid(lastEventId);
        Sinks.Many<SseNotificationEvent> sink = connectionService.registerConnection(principal.getId());
        if (sink == null) {
            return Flux.error(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "SSE connection limit reached"));
        }

        return Flux.defer(() -> sink.asFlux().publish(shared -> {
                    Mono<Void> sinkClosed = shared.ignoreElements().then();
                    Flux<ServerSentEvent<NotificationRequest>> live = shared.map(VersionedNotificationController::toSse);
                    Flux<ServerSentEvent<NotificationRequest>> replay = cursor == null
                            ? Flux.empty()
                            : notificationStore.replayNotifications(principal.getId(), cursor, REPLAY_LIMIT)
                                .takeUntilOther(sinkClosed)
                                .map(event -> toSse(new SseNotificationEvent(event.publicId(), event.notification())));
                    // Subscribe to the live sink through the returned chain before replay starts.
                    return Flux.merge(live, replay);
                }))
                .mergeWith(heartbeat())
                .doFinally(signal -> connectionService.unregisterConnection(principal.getId(), sink));
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
