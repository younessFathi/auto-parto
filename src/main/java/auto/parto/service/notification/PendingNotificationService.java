package auto.parto.service.notification;

import auto.parto.dto.NotificationRequest;
import auto.parto.service.BasicCircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class PendingNotificationService {

    private final SSENotificationService sseService;
    private final BasicCircuitBreaker circuitBreaker;

    // Store pending notifications per user
    private final Map<String, Queue<NotificationRequest>> pendingNotifications = new ConcurrentHashMap<>();
    private final int MAX_PENDING_PER_USER = 50;

    /**
     * Send all pending notifications for a user when they connect
     */
    public void sendPendingNotifications(String userId) {
        Queue<NotificationRequest> userQueue = pendingNotifications.get(userId);

        if (userQueue == null || userQueue.isEmpty()) {
            log.debug("No pending notifications for user: {}", userId);
            return;
        }

        log.info("Sending {} pending notifications to user: {}", userQueue.size(), userId);

        // Process all pending notifications
        while (!userQueue.isEmpty()) {
            NotificationRequest notification = userQueue.poll();

            // Reset circuit breaker for this delivery attempt
            circuitBreaker.recordSuccess(userId);

            boolean delivered = sseService.sendViaSSE(userId, notification);

            if (delivered) {
                log.debug("Delivered pending notification to {}: {}", userId, notification.id());
            } else {
                // Re-queue if failed (for next connection attempt)
                log.warn("Failed to deliver pending notification to {}", userId);
                userQueue.offer(notification); // Put it back
                break; // Stop trying if one fails
            }
        }

        // Clean up if queue is empty
        if (userQueue.isEmpty()) {
            pendingNotifications.remove(userId);
        }
    }

    /**
     * Add notification to pending queue
     */
    public void addPendingNotification(NotificationRequest notification) {
        String userId = notification.userId();
        pendingNotifications.compute(userId, (key, queue) -> {
            if (queue == null) {
                queue = new ArrayDeque<>();
            }

            // Enforce size limit
            if (queue.size() >= MAX_PENDING_PER_USER) {
                // Remove oldest notification
                NotificationRequest removed = queue.poll();
                log.warn("Pending queue full for user {}, removed oldest: {}", userId, removed.id());
            }

            queue.offer(notification);
            return queue;
        });

        log.info("Added pending notification for user {} (queue size: {})",
                userId, pendingNotifications.get(userId).size());
    }

    /**
     * Get count of pending notifications for a user
     */
    public int getPendingCount(String userId) {
        Queue<NotificationRequest> queue = pendingNotifications.get(userId);
        return queue != null ? queue.size() : 0;
    }

    /**
     * Clear all pending notifications for a user
     */
    public void clearPendingNotifications(String userId) {
        Queue<NotificationRequest> removed = pendingNotifications.remove(userId);
        if (removed != null) {
            log.info("Cleared {} pending notifications for user {}", removed.size(), userId);
        }
    }

    public List<NotificationRequest> getPendingNotifications(String userId) {
        Queue<NotificationRequest> queue = pendingNotifications.get(userId);
        if (queue == null || queue.isEmpty()) {
            return List.of(); // Return an empty list if no pending notifications
        }
        return List.copyOf(queue); // Convert the queue to an immutable list
    }

    //479af618-2255-4e5c-ac4e-1982f2060e1c
}
