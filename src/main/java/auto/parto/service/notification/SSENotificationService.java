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
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class SSENotificationService {

    @Value("${notification.timeout}")
    private Integer timeout;

    @Value("${notification.sse.max-connections:1000}")
    private int maxConnections;

    @Value("${notification.sse.buffer-size:1000}")
    private int bufferSize;

    // Store active connections per user
    private final Map<String, Sinks.Many<SseNotificationEvent>> userConnections = new ConcurrentHashMap<>();
    private final Object connectionRegistryLock = new Object();

    /**
     * Send notification via SSE to a specific user
     */
    public boolean sendViaSSE(String userId, NotificationRequest notification) {
        UUID eventId;
        try {
            eventId = UUID.fromString(notification.id());
        } catch (RuntimeException exception) {
            log.warn("SSE notification has no durable event ID for user {}", userId);
            return false;
        }
        return sendViaSSE(userId, eventId, notification);
    }

    public boolean sendViaSSE(String userId, UUID eventId, NotificationRequest notification) {
        try {
            if (eventId == null || notification == null) return false;
            Sinks.Many<SseNotificationEvent> userSink = userConnections.get(userId);

            if (userSink == null) {
                log.warn("No active SSE connection for user: {}", userId);
                return false;
            }

            // Attempt to send notification
            Sinks.EmitResult result = userSink.tryEmitNext(new SseNotificationEvent(eventId, notification));

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
                    return tryEmitWithBackpressure(userSink, new SseNotificationEvent(eventId, notification));

                case FAIL_ZERO_SUBSCRIBER:
                    log.warn("No subscribers for user: {}", userId);
                    cleanupConnection(userId, userSink);
                    return false;

                case FAIL_CANCELLED:
                    log.warn("SSE sink cancelled for user: {}", userId);
                    cleanupConnection(userId, userSink);
                    return false;

                case FAIL_TERMINATED:
                    log.warn("SSE sink terminated for user: {}", userId);
                    cleanupConnection(userId, userSink);
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
    public Sinks.Many<SseNotificationEvent> registerConnection(String userId) {
        synchronized (connectionRegistryLock) {
            Sinks.Many<SseNotificationEvent> existing = userConnections.get(userId);
            if (existing == null && userConnections.size() >= maxConnections) {
                log.warn("Max SSE connections reached ({}), rejecting connection for user: {}",
                        maxConnections, userId);
                return null;
            }

            Sinks.Many<SseNotificationEvent> sink = Sinks.many().unicast()
                    .onBackpressureBuffer(new ArrayBlockingQueue<>(bufferSize));
            if (existing != null) {
                existing.tryEmitComplete();
            }
            userConnections.put(userId, sink);

            log.info("SSE connection registered for user: {} (total connections: {})",
                    userId, userConnections.size());
            return sink;
        }
    }

    /**
     * Remove user's SSE connection
     */
    public void unregisterConnection(String userId) {
        synchronized (connectionRegistryLock) {
            Sinks.Many<SseNotificationEvent> current = userConnections.remove(userId);
            completeConnection(userId, current);
        }
    }

    public void unregisterConnection(String userId, Sinks.Many<SseNotificationEvent> expectedConnection) {
        synchronized (connectionRegistryLock) {
            Sinks.Many<SseNotificationEvent> current = userConnections.get(userId);
            if (current == expectedConnection) {
                userConnections.remove(userId);
                completeConnection(userId, current);
            }
        }
    }

    /**
     * Check if user has active SSE connection
     */
    public boolean hasActiveConnection(String userId) {
        Sinks.Many<SseNotificationEvent> sink = userConnections.get(userId);
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

    private boolean tryEmitWithBackpressure(Sinks.Many<SseNotificationEvent> sink, SseNotificationEvent notification) {
        try {
            Sinks.EmitResult result = sink.tryEmitNext(notification);
            if (result.isSuccess()) {
                return true;
            }
            log.warn("SSE backpressure emit failed: {}", result);
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private void cleanupConnection(String userId, Sinks.Many<SseNotificationEvent> expectedConnection) {
        synchronized (connectionRegistryLock) {
            if (userConnections.get(userId) == expectedConnection) {
                userConnections.remove(userId);
                completeConnection(userId, expectedConnection);
            }
        }
    }

    private void completeConnection(String userId, Sinks.Many<SseNotificationEvent> sink) {
        if (sink != null) {
            sink.emitComplete(Sinks.EmitFailureHandler.FAIL_FAST);
            log.info("SSE connection removed for user: {}", userId);
        }
    }
    public Sinks.Many<SseNotificationEvent> getUserConnections(String userId){
        return userConnections.get(userId);
    }
}
