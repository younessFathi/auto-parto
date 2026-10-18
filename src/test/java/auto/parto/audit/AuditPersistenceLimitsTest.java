package auto.parto.audit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuditPersistenceLimitsTest {

    @Test
    void terminalRetentionIsBoundedAndBatchSizeRemainsCapped() {
        assertThat(AuditPersistenceLimits.TERMINAL_RETENTION).isEqualTo(java.time.Duration.ofDays(7));
        assertThat(AuditPersistenceLimits.requireBatchSize(100)).isEqualTo(100);
    }
}
