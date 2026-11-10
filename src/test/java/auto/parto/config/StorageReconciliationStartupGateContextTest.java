package auto.parto.config;

import auto.parto.service.StorageReconciliationService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import reactor.core.publisher.Mono;
import reactor.test.scheduler.VirtualTimeScheduler;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StorageReconciliationStartupGateContextTest {

    @Test
    void registersWithoutDurationBeanAndUsesDefaultTimeout() {
        assertConfiguredTimeout(null, Duration.ofSeconds(30));
    }

    @Test
    void usesConfiguredTimeoutForReconciliation() {
        assertConfiguredTimeout("250ms", Duration.ofMillis(250));
    }

    private static void assertConfiguredTimeout(String configuredTimeout, Duration expectedTimeout) {
        StorageReconciliationService reconciliation = mock(StorageReconciliationService.class);
        when(reconciliation.reconcile()).thenReturn(Mono.never());
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withInitializer(context -> context.getBeanFactory()
                        .setConversionService(ApplicationConversionService.getSharedInstance()))
                .withUserConfiguration(StorageReconciliationStartupGate.class)
                .withBean(StorageReconciliationService.class, () -> reconciliation)
                .withPropertyValues(
                        "auto.parto.persistence.postgresql.cutover.enabled=true",
                        "auto.parto.persistence.postgresql.cutover.storage-reconciliation.enabled=true");
        if (configuredTimeout != null) {
            runner = runner.withPropertyValues(
                    "auto.parto.persistence.postgresql.cutover.storage-reconciliation.timeout=" + configuredTimeout);
        }

        VirtualTimeScheduler scheduler = VirtualTimeScheduler.create();
        VirtualTimeScheduler.set(scheduler);
        try {
            runner.run(context -> {
                assertThat(context).hasNotFailed()
                        .hasSingleBean(StorageReconciliationStartupGate.class)
                        .doesNotHaveBean(Duration.class);
                StorageReconciliationStartupGate gate = context.getBean(StorageReconciliationStartupGate.class);
                context.publishEvent(new ApplicationStartedEvent(new SpringApplication(), new String[0],
                        context.getSourceApplicationContext(), Duration.ZERO));
                verify(reconciliation).reconcile();

                scheduler.advanceTimeBy(expectedTimeout.minusMillis(1));
                assertThat(gate.health().block().getStatus()).isEqualTo(Status.DOWN);
                assertThat(gate.health().block().getDetails())
                        .containsEntry("reason", "storage reconciliation pending");

                scheduler.advanceTimeBy(Duration.ofMillis(1));
                assertThat(gate.health().block().getStatus()).isEqualTo(Status.DOWN);
                assertThat(gate.health().block().getDetails())
                        .containsEntry("reason", "storage reconciliation failed");
            });
        } finally {
            scheduler.dispose();
            VirtualTimeScheduler.reset();
        }
    }
}
