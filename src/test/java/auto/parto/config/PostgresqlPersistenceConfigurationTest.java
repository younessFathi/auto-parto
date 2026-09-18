package auto.parto.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

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
}
