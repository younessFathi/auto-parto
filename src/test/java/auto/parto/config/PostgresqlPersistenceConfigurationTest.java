package auto.parto.config;

import io.r2dbc.spi.ConnectionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;

import static org.mockito.Mockito.mock;

import static org.assertj.core.api.Assertions.assertThat;

class PostgresqlPersistenceConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PostgresqlReactivePersistenceConfig.class);

    @Test
    void disabledPostgresqlPersistenceDoesNotCreateTransactionInfrastructure() {
        contextRunner
                .withPropertyValues("auto.parto.persistence.postgresql.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(PostgresqlReactivePersistenceConfig.class));
    }

    @Test
    void buyerIdentityEnablesSharedTransactionInfrastructureWithoutSellerFlag() {
        contextRunner
                .withUserConfiguration(ConnectionFactoryConfiguration.class)
                .withPropertyValues(
                        "auto.parto.persistence.postgresql.enabled=false",
                        "auto.parto.persistence.postgresql.identity.enabled=false",
                        "auto.parto.persistence.postgresql.buyer-identity.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(PostgresqlReactivePersistenceConfig.class);
                    assertThat(context).hasSingleBean(ReactiveTransactionManager.class);
                    assertThat(context).hasSingleBean(TransactionalOperator.class);
                });
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ConnectionFactoryConfiguration {

        @Bean
        ConnectionFactory connectionFactory() {
            return mock(ConnectionFactory.class);
        }
    }
}
