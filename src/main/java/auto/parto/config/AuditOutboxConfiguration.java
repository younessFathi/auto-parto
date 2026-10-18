package auto.parto.config;

import auto.parto.audit.AuditOutboxRepository;
import auto.parto.audit.AuditOutboxWorker;
import auto.parto.audit.AuditProcessingProperties;
import auto.parto.audit.AuditPublisher;
import auto.parto.audit.AuditReplayAuthorizer;
import auto.parto.audit.AuditStore;
import auto.parto.audit.R2dbcAuditOutboxRepository;
import auto.parto.audit.R2dbcAuditStore;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.r2dbc.core.DatabaseClient;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "auto.parto.audit", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(AuditProcessingProperties.class)
public class AuditOutboxConfiguration {

    @Bean(name = "auditConnectionFactory")
    ConnectionFactory auditConnectionFactory(AuditProcessingProperties properties) {
        properties.validate();
        ConnectionFactoryOptions options = ConnectionFactoryOptions.parse(properties.getR2dbcUrl())
                .mutate()
                .option(ConnectionFactoryOptions.USER, properties.getUsername())
                .option(ConnectionFactoryOptions.PASSWORD, properties.getPassword())
                .build();
        return ConnectionFactories.get(options);
    }

    @Bean(name = "auditDatabaseClient")
    DatabaseClient auditDatabaseClient(@Qualifier("auditConnectionFactory") ConnectionFactory connectionFactory) {
        return DatabaseClient.create(connectionFactory);
    }

    @Bean
    AuditOutboxRepository auditOutboxRepository(@Qualifier("databaseClient") DatabaseClient databaseClient) {
        return new R2dbcAuditOutboxRepository(databaseClient);
    }

    @Bean
    AuditStore auditStore(@Qualifier("auditDatabaseClient") DatabaseClient databaseClient) {
        return new R2dbcAuditStore(databaseClient);
    }

    @Bean
    AuditReplayAuthorizer auditReplayAuthorizer() {
        return (requester, eventId) -> reactor.core.publisher.Mono.just(requester != null && !requester.isBlank());
    }

    @Bean
    AuditPublisher auditPublisher(
            AuditOutboxRepository outbox,
            AuditStore auditStore,
            AuditProcessingProperties properties,
            AuditReplayAuthorizer replayAuthorizer) {
        return new AuditPublisher(outbox, auditStore, Clock.systemUTC(), properties.getMaxAttempts(),
                properties.getMaxConcurrency(), properties.getBaseRetryDelay(), properties.getMaxRetryDelay(),
                replayAuthorizer);
    }

    @Bean
    AuditOutboxWorker auditOutboxWorker(AuditPublisher publisher, AuditProcessingProperties properties) {
        return new AuditOutboxWorker(publisher, properties, Clock.systemUTC());
    }
}
