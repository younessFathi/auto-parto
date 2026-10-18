package auto.parto.config;

import io.r2dbc.spi.ConnectionFactory;
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
                    assertThat(context).hasBean("auditDatabaseClient");
                });
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
