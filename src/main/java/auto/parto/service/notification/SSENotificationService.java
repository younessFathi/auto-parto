package auto.parto.service.notification;

import auto.parto.config.DomainMetrics;
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
            log.warn("SSE notification has no durable event ID");
            return false;
        }
        return sendViaSSE(userId, eventId, notification);
    }

    public boolean sendViaSSE(String userId, UUID eventId, NotificationRequest notification) {
        try {
            if (eventId == null || notification == null) return false;
            Sinks.Many<SseNotificationEvent> userSink = userConnections.get(userId);

            if (userSink == null) {
                DomainMetrics.sseEmit("no_subscriber");
                log.warn("No active SSE connection for notification delivery");
                return false;
            }

            // Attempt to send notification
            Sinks.EmitResult result = userSink.tryEmitNext(new SseNotificationEvent(eventId, notification));

            switch (result) {
                case OK:
                    DomainMetrics.sseEmit("success");
                    log.debug("SSE notification emitted");
                    return true;

                case FAIL_NON_SERIALIZED:
                    DomainMetrics.sseEmit("failure");
                    log.warn("SSE serialization failed");
                    return false;

                case FAIL_OVERFLOW:
                    DomainMetrics.sseEmit("overflow");
                    log.warn("SSE backpressure overflow");
                    // Try with backpressure handling
                    return tryEmitWithBackpressure(userSink, new SseNotificationEvent(eventId, notification));

                case FAIL_ZERO_SUBSCRIBER:
                    DomainMetrics.sseEmit("no_subscriber");
                    log.warn("SSE stream has no subscriber");
                    cleanupConnection(userId, userSink);
                    return false;

                case FAIL_CANCELLED:
                    DomainMetrics.sseEmit("cancelled");
                    log.warn("SSE stream was cancelled");
                    cleanupConnection(userId, userSink);
                    return false;

                case FAIL_TERMINATED:
                    DomainMetrics.sseEmit("terminated");
                    log.warn("SSE stream was terminated");
                    cleanupConnection(userId, userSink);
                    return false;

                default:
                    DomainMetrics.sseEmit("failure");
                    log.error("Unknown SSE emit result: {}", result);
                    return false;
            }

        } catch (Exception e) {
            DomainMetrics.sseEmit("failure");
            log.error("Error emitting SSE notification");
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
                DomainMetrics.sseConnectionRejected();
                log.warn("Max SSE connections reached ({}), rejecting connection", maxConnections);
                return null;
            }

            Sinks.Many<SseNotificationEvent> sink = Sinks.many().unicast()
                    .onBackpressureBuffer(new ArrayBlockingQueue<>(bufferSize));
            if (existing != null) {
                existing.tryEmitComplete();
            }
            userConnections.put(userId, sink);
            DomainMetrics.setRegisteredSseConnections(userConnections.size());
            if (existing == null) DomainMetrics.sseConnectionOpened();

            log.info("SSE connection registered (total connections: {})", userConnections.size());
            return sink;
        }
    }

    /**
     * Remove user's SSE connection
     */
    public void unregisterConnection(String userId) {
        synchronized (connectionRegistryLock) {
            Sinks.Many<SseNotificationEvent> current = userConnections.remove(userId);
            if (current != null) {
                DomainMetrics.setRegisteredSseConnections(userConnections.size());
                DomainMetrics.sseConnectionClosed();
            }
            completeConnection(userId, current);
        }
    }

    public void unregisterConnection(String userId, Sinks.Many<SseNotificationEvent> expectedConnection) {
        synchronized (connectionRegistryLock) {
            Sinks.Many<SseNotificationEvent> current = userConnections.get(userId);
            if (current == expectedConnection) {
                userConnections.remove(userId);
                DomainMetrics.setRegisteredSseConnections(userConnections.size());
                DomainMetrics.sseConnectionClosed();
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
                DomainMetrics.sseEmit("success");
                return true;
            }
            DomainMetrics.sseEmit("failure");
            log.warn("SSE backpressure emit failed: {}", result);
            return false;
        } catch (Exception e) {
            DomainMetrics.sseEmit("failure");
            return false;
        }
    }

    private void cleanupConnection(String userId, Sinks.Many<SseNotificationEvent> expectedConnection) {
        synchronized (connectionRegistryLock) {
            if (userConnections.get(userId) == expectedConnection) {
                userConnections.remove(userId);
                DomainMetrics.setRegisteredSseConnections(userConnections.size());
                DomainMetrics.sseConnectionClosed();
                completeConnection(userId, expectedConnection);
            }
        }
    }

    private void completeConnection(String userId, Sinks.Many<SseNotificationEvent> sink) {
        if (sink != null) {
            sink.emitComplete(Sinks.EmitFailureHandler.FAIL_FAST);
            log.info("SSE connection removed");
        }
    }
    public Sinks.Many<SseNotificationEvent> getUserConnections(String userId){
        return userConnections.get(userId);
    }
}
