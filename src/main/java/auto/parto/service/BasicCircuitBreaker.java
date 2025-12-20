package auto.parto.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class BasicCircuitBreaker {
    private final Map<String, Integer> failureCounts = new ConcurrentHashMap<>();
    private final Map<String, Long> circuitOpenedAt = new ConcurrentHashMap<>();

    public boolean canSendToUser(String userId) {
        // Check if circuit is open
        Long openedAt = circuitOpenedAt.get(userId);
        if (openedAt != null) {
            // If opened more than 30 seconds ago, allow one try
            if (System.currentTimeMillis() - openedAt > 30000) {
                circuitOpenedAt.remove(userId); // Remove to allow retry
                return true;
            }
            return false; // Circuit is still open
        }
        return true; // Circuit is closed, allow sending
    }

    public void recordFailure(String userId) {
        int failures = failureCounts.getOrDefault(userId, 0) + 1;
        failureCounts.put(userId, failures);

        // Open circuit after 3 failures
        if (failures >= 3) {
            circuitOpenedAt.put(userId, System.currentTimeMillis());
            failureCounts.remove(userId); // Reset failure count
        }
    }

    public void recordSuccess(String userId) {
        // Reset on success
        failureCounts.remove(userId);
        circuitOpenedAt.remove(userId);
    }
}