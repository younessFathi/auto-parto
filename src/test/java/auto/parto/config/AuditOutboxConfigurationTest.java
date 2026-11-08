package auto.parto.config;

import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.pool.ConnectionPool;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.r2dbc.core.DatabaseClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AuditOutboxConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AuditOutboxConfiguration.class, PrimaryDatabaseConfiguration.class);

    @Test
    void disabledAuditProcessingDoesNotCreateWorkerOrAuditConnection() {
        contextRunner
                .withPropertyValues("auto.parto.audit.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(AuditOutboxConfiguration.class);
                    assertThat(context).doesNotHaveBean("auditConnectionFactory");
                });
    }

    @Test
    void enabledAuditProcessingCreatesSeparateStorePublisherAndWorker() {
        contextRunner
                .withPropertyValues(
                        "auto.parto.audit.enabled=true",
                        "auto.parto.audit.r2dbc-url=r2dbc:postgresql://audit.example.test/audit",
                        "auto.parto.audit.username=audit_writer",
                        "auto.parto.audit.password=test-only")
                .run(context -> {
                    assertThat(context).hasSingleBean(auto.parto.audit.AuditPublisher.class);
                    assertThat(context).hasSingleBean(auto.parto.audit.AuditOutboxWorker.class);
                    assertThat(context).hasBean("auditConnectionFactory");
                    assertThat(context.getBean("auditConnectionFactory")).isInstanceOf(ConnectionPool.class);
                    assertThat(context).hasSingleBean(io.micrometer.core.instrument.binder.MeterBinder.class);
                    assertThat(context).hasBean("auditDatabaseClient");
                    MeterRegistry registry = new SimpleMeterRegistry();
                    context.getBean(MeterBinder.class).bindTo(registry);
                    assertThat(registry.find("auto.parto.postgresql.pool.connections")
                            .tag("pool", "audit-postgresql")
                            .gauges()).hasSize(4);
                });
    }

    @Test
    void enabledAuditProcessingRejectsMissingRuntimeCredentialsBeforeCreatingWorker() {
        contextRunner
                .withPropertyValues(
                        "auto.parto.audit.enabled=true",
                        "auto.parto.audit.r2dbc-url=r2dbc:postgresql://audit.example.test/audit")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasMessageContaining("username is required"));
    }

    @Test
    void enabledAuditProcessingRejectsInvalidWorkerSettings() {
        contextRunner
                .withPropertyValues(
                        "auto.parto.audit.enabled=true",
                        "auto.parto.audit.r2dbc-url=r2dbc:postgresql://audit.example.test/audit",
                        "auto.parto.audit.username=audit_writer",
                        "auto.parto.audit.password=test-only",
                        "auto.parto.audit.poll-interval=0s")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasMessageContaining("poll-interval must be positive"));
    }

    @Test
    void auditWorkerConcurrencyCannotExceedItsBoundedConnectionPool() {
        contextRunner
                .withPropertyValues(
                        "auto.parto.audit.enabled=true",
                        "auto.parto.audit.r2dbc-url=r2dbc:postgresql://audit.example.test/audit",
                        "auto.parto.audit.username=audit_writer",
                        "auto.parto.audit.password=test-only",
                        "auto.parto.audit.max-connections=3",
                        "auto.parto.audit.max-concurrency=4")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasMessageContaining("max-concurrency must not exceed max-connections"));
    }

    @Configuration(proxyBeanMethods = false)
    static class PrimaryDatabaseConfiguration {

        @Bean
        ConnectionFactory connectionFactory() {
            return mock(ConnectionFactory.class);
        }

        @Bean(name = "databaseClient")
        DatabaseClient databaseClient() {
            return mock(DatabaseClient.class);
        }
    }
}
