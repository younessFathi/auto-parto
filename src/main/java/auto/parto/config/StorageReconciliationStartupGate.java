package auto.parto.config;

import auto.parto.service.StorageReconciliationService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Blocks explicit PostgreSQL cutover startup until file metadata and storage agree. */
@Component
@Order(0)
@ConditionalOnProperty(prefix = "auto.parto.persistence.postgresql.cutover.storage-reconciliation",
        name = "enabled", havingValue = "true")
public final class StorageReconciliationStartupGate implements ApplicationRunner {

    private final StorageReconciliationService reconciliation;
    private final Duration timeout;

    public StorageReconciliationStartupGate(StorageReconciliationService reconciliation,
                                            @org.springframework.beans.factory.annotation.Value(
                                                    "${auto.parto.persistence.postgresql.cutover.storage-reconciliation.timeout:30s}") Duration timeout) {
        this.reconciliation = reconciliation;
        this.timeout = timeout;
    }

    @Override
    public void run(ApplicationArguments args) {
        var report = reconciliation.reconcile().block(timeout);
        if (report == null || !report.healthy()) {
            throw new IllegalStateException("PostgreSQL cutover storage reconciliation failed: "
                    + (report == null ? "no report" : report.missingObjectKeys()));
        }
    }
}
