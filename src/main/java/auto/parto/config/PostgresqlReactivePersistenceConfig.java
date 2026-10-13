package auto.parto.config;

import io.r2dbc.spi.ConnectionFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;

@Configuration
@ConditionalOnClass({ConnectionFactory.class, R2dbcTransactionManager.class})
@ConditionalOnExpression("'${auto.parto.persistence.postgresql.enabled:false}' == 'true' || "
        + "'${auto.parto.persistence.postgresql.identity.enabled:false}' == 'true' || "
        + "'${auto.parto.persistence.postgresql.buyer-identity.enabled:false}' == 'true'")
public class PostgresqlReactivePersistenceConfig {

    @Bean
    @ConditionalOnMissingBean(ReactiveTransactionManager.class)
    ReactiveTransactionManager reactiveTransactionManager(ConnectionFactory connectionFactory) {
        return new R2dbcTransactionManager(connectionFactory);
    }

    @Bean
    @ConditionalOnMissingBean
    TransactionalOperator transactionalOperator(ReactiveTransactionManager transactionManager) {
        return TransactionalOperator.create(transactionManager);
    }
}
