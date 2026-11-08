package auto.parto.config;

import auto.parto.audit.AuditOutboxRepository;
import auto.parto.audit.AuditOutboxWorker;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.pool.PoolMetrics;
import org.springframework.beans.factory.ObjectProvider;
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
import java.time.Duration;
import java.util.function.ToIntFunction;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "auto.parto.audit", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(AuditProcessingProperties.class)
public class AuditOutboxConfiguration {

    @Bean(name = "auditConnectionFactory", destroyMethod = "dispose")
    ConnectionPool auditConnectionFactory(AuditProcessingProperties properties) {
        properties.validate();
        ConnectionFactoryOptions options = ConnectionFactoryOptions.parse(properties.getR2dbcUrl())
                .mutate()
                .option(ConnectionFactoryOptions.USER, properties.getUsername())
                .option(ConnectionFactoryOptions.PASSWORD, properties.getPassword())
                .build();
        ConnectionFactory delegate = ConnectionFactories.get(options);
        return new ConnectionPool(ConnectionPoolConfiguration.builder(delegate)
                .initialSize(0)
                .maxSize(properties.getMaxConnections())
                .maxAcquireTime(properties.getMaxAcquireTime())
                .maxCreateConnectionTime(properties.getMaxCreateConnectionTime())
                .maxIdleTime(Duration.ofMinutes(30))
                .maxLifeTime(Duration.ofMinutes(30))
                .name("audit-postgresql")
                .build());
    }

    @Bean
    MeterBinder auditPostgresqlPoolMetrics(@Qualifier("auditConnectionFactory") ConnectionPool pool) {
        return registry -> {
            registerPoolGauge(registry, pool, "allocated", PoolMetrics::allocatedSize);
            registerPoolGauge(registry, pool, "acquired", PoolMetrics::acquiredSize);
            registerPoolGauge(registry, pool, "idle", PoolMetrics::idleSize);
            registerPoolGauge(registry, pool, "pending", PoolMetrics::pendingAcquireSize);
        };
    }

    private static void registerPoolGauge(
            MeterRegistry registry,
            ConnectionPool pool,
            String state,
            ToIntFunction<PoolMetrics> value) {
        Gauge.builder("auto.parto.postgresql.pool.connections", pool,
                        connectionPool -> connectionPool.getMetrics().map(value::applyAsInt).orElse(0))
                .tag("pool", "audit-postgresql")
                .tag("state", state)
                .register(registry);
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
    AuditOutboxWorker auditOutboxWorker(AuditPublisher publisher, AuditProcessingProperties properties,
                                        ObjectProvider<ObservationRegistry> observationRegistry) {
        return new AuditOutboxWorker(publisher, properties, Clock.systemUTC(),
                observationRegistry.getIfAvailable(() -> ObservationRegistry.NOOP));
    }
}
