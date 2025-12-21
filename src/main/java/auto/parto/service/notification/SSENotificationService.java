package auto.parto.service.notification;

import auto.parto.dto.NotificationRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class SSENotificationService {

    @Value("${notification.timeout}")
    private Integer timeout;

    // Store active connections per user
    private final Map<String, Sinks.Many<NotificationRequest>> userConnections = new ConcurrentHashMap<>();

    /**
     * Send notification via SSE to a specific user
     */
    public boolean sendViaSSE(String userId, NotificationRequest notification) {
        try {
            Sinks.Many<NotificationRequest> userSink = userConnections.get(userId);

            if (userSink == null) {
                log.warn("No active SSE connection for user: {}", userId);
                return false;
            }

            // Attempt to send notification
            Sinks.EmitResult result = userSink.tryEmitNext(notification);

            switch (result) {
                case OK:
                    log.debug("SSE notification sent to user: {}", userId);
                    return true;

                case FAIL_NON_SERIALIZED:
                    log.warn("SSE serialization failed for user: {}", userId);
                    return false;

                case FAIL_OVERFLOW:
                    log.warn("SSE backpressure overflow for user: {}", userId);
                    // Try with backpressure handling
                    return tryEmitWithBackpressure(userSink, notification);

                case FAIL_ZERO_SUBSCRIBER:
                    log.warn("No subscribers for user: {}", userId);
                    cleanupConnection(userId);
                    return false;

                case FAIL_CANCELLED:
                    log.warn("SSE sink cancelled for user: {}", userId);
                    cleanupConnection(userId);
                    return false;

                case FAIL_TERMINATED:
                    log.warn("SSE sink terminated for user: {}", userId);
                    cleanupConnection(userId);
                    return false;

                default:
                    log.error("Unknown SSE emit result: {} for user: {}", result, userId);
                    return false;
            }

        } catch (Exception e) {
            log.error("Error sending SSE to user: {}", userId, e);
            return false;
        }
    }

    /**
     * Send to multiple users (e.g., multiple sellers for a search)
     */
    public Map<String, Boolean> sendViaSSEBulk(List<String> userIds, NotificationRequest notification) {
        Map<String, Boolean> results = new ConcurrentHashMap<>();

        userIds.parallelStream().forEach(userId -> {
            boolean success = sendViaSSE(userId, notification);
            results.put(userId, success);
        });

        return results;
    }

    /**
     * Register a user's SSE connection
     */
    public Sinks.Many<NotificationRequest> registerConnection(String userId) {
        // Create a new sink for this user
        Sinks.Many<NotificationRequest> sink = Sinks.many().unicast().onBackpressureBuffer();

        userConnections.put(userId, sink);

        log.info("SSE connection registered for user: {} (total connections: {})",
                userId, userConnections.size());

        return sink;
    }

    /**
     * Remove user's SSE connection
     */
    public void unregisterConnection(String userId) {
        Sinks.Many<NotificationRequest> sink = userConnections.remove(userId);
        if (sink != null) {
            sink.emitComplete(Sinks.EmitFailureHandler.FAIL_FAST);
            log.info("SSE connection removed for user: {}", userId);
        }
    }

    /**
     * Check if user has active SSE connection
     */
    public boolean hasActiveConnection(String userId) {
        Sinks.Many<NotificationRequest> sink = userConnections.get(userId);
        return sink != null && sink.currentSubscriberCount() > 0;
    }

    /**
     * Get connection statistics
     */
    public Map<String, Object> getConnectionStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers", userConnections.size());

        long activeConnections = userConnections.values().stream()
                .filter(sink -> sink.currentSubscriberCount() > 0)
                .count();

        stats.put("activeConnections", activeConnections);

        return stats;
    }

    // Private helper methods

    private boolean tryEmitWithBackpressure(Sinks.Many<NotificationRequest> sink, NotificationRequest notification) {
        try {
            // Try with a timeout
            // Just to subscribe
            return Boolean.TRUE.equals(sink.asFlux()
                    .doOnNext(n -> {
                    }) // Just to subscribe
                    .timeout(Duration.ofMillis(timeout))
                    .then(Mono.fromRunnable(() -> {
                        sink.emitNext(notification, Sinks.EmitFailureHandler.FAIL_FAST);
                    }))
                    .thenReturn(true)
                    .onErrorReturn(false)
                    .block());
        } catch (Exception e) {
            return false;
        }
    }

    private void cleanupConnection(String userId) {
        Sinks.Many<NotificationRequest> sink = userConnections.remove(userId);
        if (sink != null) {
            sink.emitComplete(Sinks.EmitFailureHandler.FAIL_FAST);
        }
    }
    public  Sinks.Many<NotificationRequest> getUserConnections(String userId){
        return userConnections.get(userId);
    }
}
