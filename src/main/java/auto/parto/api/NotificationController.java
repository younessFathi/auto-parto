package auto.parto.api;

import auto.parto.dto.NotificationRequest;
import auto.parto.enums.NotificationStatus;
import auto.parto.service.notification.PendingNotificationService;
import auto.parto.service.notification.ResilientNotificationService;
import auto.parto.service.notification.ResilientPendingNotificationService;
import auto.parto.service.notification.SSENotificationService;
import auto.parto.security.CanonicalSecurity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import static auto.parto.utils.Constants.Notification.*;


@RestController
@RequestMapping("/notifications")
@Slf4j
public class NotificationController {
    // Use Sinks for handling multiple subscribers
    private final Sinks.Many<NotificationRequest> notificationSink;
    private final ResilientNotificationService resilientNotificationService;
    private final PendingNotificationService pendingNotificationService;
    private final ResilientPendingNotificationService resilientPendingNotificationService;
    private final SSENotificationService sseNotificationService;

    public NotificationController(
            ResilientNotificationService resilientNotificationService,
            PendingNotificationService pendingNotificationService,
            ResilientPendingNotificationService resilientPendingNotificationService,
            SSENotificationService sseNotificationService
    ) {
        this.resilientNotificationService = resilientNotificationService;
        this.pendingNotificationService = pendingNotificationService;
        this.sseNotificationService = sseNotificationService;
        this.notificationSink = Sinks.many().multicast().onBackpressureBuffer();
        this.resilientPendingNotificationService = resilientPendingNotificationService;
    }

    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<NotificationRequest>> subscribe() {
        return notificationSink.asFlux()
                .map(this::createServerSentEvent)
                .mergeWith(heartbeatFlux())
                .doOnSubscribe(sub -> log.info(NEW_SUBSCRIBER_CONNECTED))
                .doOnCancel(() -> log.info(SUBSCRIBER_DISCONNECTED))
                .doOnError(error -> log.error("{} : {}",SSE_CONNECTION_ERROR , error.getMessage()))
                .onErrorResume(error -> {
                    log.error(SSE_ERROR_RETURNING_EMPTY_FLUX, error);
                    return Flux.empty();
                });
    }

    private ServerSentEvent<NotificationRequest> createServerSentEvent(NotificationRequest notification) {
        return ServerSentEvent.<NotificationRequest>builder()
                .id(notification.id())
                .event(notification.type().name())
                .data(notification)
                .retry(Duration.ofSeconds(10))
                .build();
    }

    // Subscribe with user-specific notifications
    @GetMapping(value = "/subscribe/{userId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<NotificationRequest>> subscribeToUser(
            @PathVariable String userId,
            org.springframework.security.core.Authentication authentication) {
        CanonicalSecurity.requireOwner(authentication, userId, null);
        Sinks.Many<NotificationRequest> userSink = resilientNotificationService.registerConnection(userId);
        return resilientPendingNotificationService.deliverPendingOnConnect(userId).thenMany(
            sseNotificationService.getUserConnections(userId)
                    .asFlux()
                    .map(buildNotification())
                )
                .doOnSubscribe(sub -> log.info(USER_SUBSCRIBED_TO_SSE, userId))
                .doOnCancel(() -> {
                    log.info(USER_SSE_CONNECTION_CLOSED, userId);
                    resilientNotificationService.unregisterConnection(userId);
                })
                .doOnError(error -> {
                    log.error(SSE_ERROR_FOR_USER, userId, error.getMessage());
                    resilientNotificationService.unregisterConnection(userId);
                });
    }

    private static Function<NotificationRequest, ServerSentEvent<NotificationRequest>> buildNotification() {
        return notification -> {
            // Format as proper SSE
            return ServerSentEvent.<NotificationRequest>builder()
                    .id(UUID.randomUUID().toString())
                    .event(notification.type().name())
                    .data(notification)
                    .retry(Duration.ofSeconds(10))
                    .build();
        };
    }

    // Broadcast notification to all connected clients
    @PostMapping("/broadcast")
    public Mono<Void> broadcast(@RequestBody NotificationRequest notificationRequest) {
        return Mono.fromRunnable(() -> {
            notificationSink.tryEmitNext(notificationRequest);
            log.info("Broadcast message {} " , notificationRequest);
        });
    }

    // Send notification to specific user
    @PostMapping("/send")
    public Mono<ResponseEntity<Map<String, Object>>> sendToUser(
            @RequestBody NotificationRequest notificationRequest) {
        String userId = notificationRequest.userId();
        log.info(SENDING_NOTIFICATION_TO_USER_TYPE, userId, notificationRequest.type());
        return Mono.defer(() -> {
                    // Check if user has active connection
                    if (resilientNotificationService.hasActiveConnection(userId)) {
                        log.debug(USER_HAS_ACTIVE_CONNECTION_ATTEMPTING_DIRECT_DELIVERY, userId);

                        // User is online - try direct delivery
                        boolean delivered = resilientNotificationService.sendNotification(notificationRequest);

                        if (delivered) {
                            log.info(NOTIFICATION_SUCCESSFULLY_DELIVERED_TO_VIA_SSE, userId);
                            return Mono.just(ResponseEntity.ok(createSuccessResponse(userId)));
                        }

                        log.warn(DIRECT_DELIVERY_FAILED_FOR_FALLING_BACK_TO_STORAGE, userId);
                    } else {
                        log.debug(USER_IS_OFFLINE_STORING_NOTIFICATION, userId);
                    }

                    // Store for later delivery
                    return pendingNotificationService.addPendingNotification(notificationRequest)
                            .map(isStored -> {
                                if (isStored) {
                                    log.info(NOTIFICATION_STORED_FOR_USER, userId);
                                    return ResponseEntity.status(HttpStatus.ACCEPTED)
                                            .body(createPendingResponse(userId));
                                } else {
                                    log.error(FAILED_TO_STORE_NOTIFICATION_FOR_USER, userId);
                                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                            .body(createErrorResponse(userId, STORAGE_FAILED));
                                }
                            });
                })
                .onErrorResume(error -> {
                    log.error(ERROR_SENDING_NOTIFICATION_TO, userId, error.getMessage(), error);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(createErrorResponse(userId, error.getMessage())));
                })
                .defaultIfEmpty(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(createErrorResponse(userId, INVALID_NOTIFICATION_REQUEST)));
    }

    // Helper methods for consistent response formatting
    private Map<String, Object> createSuccessResponse(String userId) {
        return Map.of(
                STATUS, NotificationStatus.DELIVERED,
                USER_ID, userId,
                METHOD, SSE,
                TIMESTAMP, LocalDateTime.now(),
                MESSAGE, NOTIFICATION_DELIVERED_SUCCESSFULLY
        );
    }

    private Map<String, Object> createPendingResponse(String userId) {
        return Map.of(
                STATUS, NotificationStatus.PENDING,
                USER_ID, userId,
                METHOD, STORED,
                TIMESTAMP, LocalDateTime.now(),
                MESSAGE, USER_OFFLINE_NOTIFICATION_STORED_FOR_LATER_DELIVERY
        );
    }

    private Map<String, Object> createErrorResponse(String userId, String error) {
        return Map.of(
                STATUS, ERROR.toUpperCase(),
                USER_ID, userId,
                TIMESTAMP, LocalDateTime.now(),
                ERROR, error,
                MESSAGE, FAILED_TO_PROCESS_NOTIFICATION
        );
    }

    // Clean heartbeat implementation
    private Flux<ServerSentEvent<NotificationRequest>> heartbeatFlux() {
        return Flux.interval(Duration.ofSeconds(30))
                .map(tick -> ServerSentEvent.<NotificationRequest>builder()
                        .comment(HEARTBEAT)  // SSE comment, not data
                        .build())
                .onBackpressureDrop()
                .doOnNext(hb -> log.info(SENT_HEARTBEAT_AT, LocalDateTime.now()));
    }

}
