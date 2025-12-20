package auto.parto.api;

import auto.parto.dto.NotificationRequest;
import auto.parto.service.notification.PendingNotificationService;
import auto.parto.service.notification.ResilientNotificationService;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

@RestController
@RequestMapping("/notifications")
@Slf4j
public class NotificationController {

    // Use Sinks for handling multiple subscribers
    private final Sinks.Many<String> notificationSink;
    private final Map<String, Sinks.Many<String>> userSinks;
    private final ResilientNotificationService resilientNotificationService;
    private final PendingNotificationService pendingNotificationService;

    public NotificationController(ResilientNotificationService resilientNotificationService, PendingNotificationService pendingNotificationService) {
        this.resilientNotificationService = resilientNotificationService;
        this.pendingNotificationService = pendingNotificationService;
        this.notificationSink = Sinks.many().multicast().onBackpressureBuffer();
        this.userSinks = new ConcurrentHashMap<>();
    }

    // Subscribe to general notifications (public feed)
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> subscribe() {
        return notificationSink.asFlux()
                .map(message -> ServerSentEvent.builder(message)
                        .id(UUID.randomUUID().toString())
                        .event("notification")
                        .build())
                // Add heartbeat manually for better control
                .mergeWith(heartbeatFlux())
                .doOnSubscribe(sub -> System.out.println("New subscriber"))
                .doOnCancel(() -> System.out.println("Subscriber disconnected"));
    }

    // Subscribe with user-specific notifications
    @GetMapping(value = "/subscribe/{userId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<NotificationRequest>> subscribeToUser(@PathVariable String userId) {
        Sinks.Many<NotificationRequest> userSink = resilientNotificationService.registerConnection(userId);
        return Flux.concat(
                        Flux.defer(processPendingNotifications(userId)),// PHASE 1: Send pending notifications FIRST (as soon as user subscribes)
                        userSink.asFlux()// PHASE 2: Then stream live notifications
                )
                .map(buildNotification())
                .doOnSubscribe(sub -> {
                    log.info("User {} subscribed to SSE", userId);
                })
                .doOnCancel(() -> {
                    log.info("User {} SSE connection closed", userId);
                    resilientNotificationService.unregisterConnection(userId);
                })
                .doOnError(error -> {
                    log.error("SSE error for user {}: {}", userId, error.getMessage());
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

    private Supplier<Publisher<NotificationRequest>> processPendingNotifications(String userId) {
        return () -> {
            log.info("Sending pending notifications to {}", userId);

            // Get pending notifications as a list
            List<NotificationRequest> pending = pendingNotificationService.getPendingNotifications(userId);

            if (pending.isEmpty()) log.info("No pending notification");
            else pendingNotificationService.clearPendingNotifications(userId);

            return Flux.fromIterable(pending);
        };
    }

    // Broadcast notification to all connected clients
    @PostMapping("/broadcast")
    public Mono<Void> broadcast(@RequestBody NotificationRequest request) {
        return Mono.fromRunnable(() -> {
            String message = formatMessage(request);
            notificationSink.tryEmitNext(message);
            log.info("Broadcast message {} " , message);
        });
    }

    // Send notification to specific user
    @PostMapping("/send")
    public ResponseEntity<Map<String, Object>> sendToUser(
            @RequestBody NotificationRequest notificationRequest) {
        String userId = notificationRequest.userId();
        log.info("Sending notification for user {} " , userId);
        // Check if user has active connection
        if (resilientNotificationService.hasActiveConnection(userId)) {
            // User is online - try direct delivery
            boolean delivered = resilientNotificationService.sendNotification(notificationRequest);

            if (delivered) {
                return ResponseEntity.ok(Map.of(
                        "status", "DELIVERED",
                        "userId", userId,
                        "method", "SSE"
                ));
            }
        }
        pendingNotificationService.addPendingNotification(notificationRequest);
        int pendingCount = pendingNotificationService.getPendingCount(userId);

        return ResponseEntity.status(202).body(Map.of(
                "status", "PENDING",
                "userId", userId,
                "method", "STORED",
                "pendingCount", pendingCount,
                "message", "User offline. Notification stored for later delivery."
        ));
    }

    // Clean heartbeat implementation
    private Flux<ServerSentEvent<String>> heartbeatFlux() {
        return Flux.interval(Duration.ofSeconds(30))
                .map(tick -> ServerSentEvent.<String>builder()
                        .comment("heartbeat")  // SSE comment, not data
                        .build())
                .onBackpressureDrop()
                .doOnNext(hb -> System.out.println("Sent heartbeat at " +
                        LocalDateTime.now()));
    }

    private void cleanupUserSink(String userId) {
        Sinks.Many<String> sink = userSinks.remove(userId);
        if (sink != null) {
            sink.tryEmitComplete();
        }
    }

    private String formatMessage(NotificationRequest request) {
        return String.format("{\"type\":\"%s\",\"message\":\"%s\",\"timestamp\":%d}",
                request.type(),
                request.message(),
                System.currentTimeMillis());
    }
}

