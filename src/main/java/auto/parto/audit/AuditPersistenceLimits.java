package auto.parto.audit;

import java.time.Duration;

public final class AuditPersistenceLimits {

    public static final int MAX_BATCH_SIZE = 100;
    public static final Duration TERMINAL_RETENTION = Duration.ofDays(7);

    private AuditPersistenceLimits() {
    }

    public static int requireBatchSize(int requested) {
        if (requested < 1 || requested > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("batch size must be between 1 and " + MAX_BATCH_SIZE);
        }
        return requested;
    }
}
