package auto.parto.api;

import auto.parto.dto.NotificationRequest;
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
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    // Use Sinks for handling multiple subscribers
    private final Sinks.Many<String> notificationSink;
    private final Map<String, Sinks.Many<String>> userSinks;

    public NotificationController() {
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
    public Flux<ServerSentEvent<String>> subscribeToUser(
            @PathVariable String userId,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {

        // Handle reconnection with Last-Event-ID (for missed events)
        System.out.println("User " + userId + " connected. Last event ID: " + lastEventId);

        // Create or get user-specific sink
        Sinks.Many<String> userSink = userSinks.computeIfAbsent(
                userId,
                key -> Sinks.many().multicast().onBackpressureBuffer()
        );

        // Send initial connection event
        userSink.tryEmitNext("Connected to user notification stream");

        // Return SSE stream with proper formatting
        return userSink.asFlux()
                .map(data -> ServerSentEvent.<String>builder()
                        .id(String.valueOf(System.currentTimeMillis()))
                        .event("notification")
                        .data(data)
                        .retry(Duration.ofSeconds(5))
                        .build())
                .doOnCancel(() -> {
                    System.out.println("User " + userId + " disconnected");
                    cleanupUserSink(userId);
                });
    }

    // Broadcast notification to all connected clients
    @PostMapping("/broadcast")
    public Mono<Void> broadcast(@RequestBody NotificationRequest request) {
        return Mono.fromRunnable(() -> {
            String message = formatMessage(request);
            notificationSink.tryEmitNext(message);
            System.out.println("Broadcasted: " + message);
        });
    }

    // Send notification to specific user
    @PostMapping("/send/{userId}")
    public Mono<ResponseEntity<String>> sendToUser(
            @PathVariable String userId,
            @RequestBody NotificationRequest request) {

        return Mono.fromSupplier(() -> {
            String message = formatMessage(request);
            Sinks.Many<String> userSink = userSinks.get(userId);

            if (userSink != null) {
                userSink.tryEmitNext(message);
                return ResponseEntity.ok("Notification sent to user " + userId);
            } else {
                return ResponseEntity.status(404)
                        .body("User " + userId + " is not connected");
            }
        });
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

