package auto.parto.persistence;

import io.r2dbc.spi.ConnectionFactoryOptions;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;

import java.util.UUID;

import static io.r2dbc.spi.ConnectionFactoryOptions.DATABASE;
import static io.r2dbc.spi.ConnectionFactoryOptions.DRIVER;
import static io.r2dbc.spi.ConnectionFactoryOptions.HOST;
import static io.r2dbc.spi.ConnectionFactoryOptions.PASSWORD;
import static io.r2dbc.spi.ConnectionFactoryOptions.PORT;
import static io.r2dbc.spi.ConnectionFactoryOptions.USER;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class R2dbcDeviceTokenRepositoryIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private DatabaseClient databaseClient;
    private DeviceTokenRepository repository;

    @BeforeAll
    void migrateAndCreateRepository() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        var connectionFactory = io.r2dbc.spi.ConnectionFactories.get(
                ConnectionFactoryOptions.builder()
                        .option(DRIVER, "postgresql")
                        .option(HOST, POSTGRES.getHost())
                        .option(PORT, POSTGRES.getFirstMappedPort())
                        .option(USER, POSTGRES.getUsername())
                        .option(PASSWORD, POSTGRES.getPassword())
                        .option(DATABASE, POSTGRES.getDatabaseName())
                        .build());
        databaseClient = DatabaseClient.create(connectionFactory);
        repository = new R2dbcDeviceTokenRepository(databaseClient,
                TransactionalOperator.create(new R2dbcTransactionManager(connectionFactory)));
    }

    @Test
    void registersAndListsActiveTokenByCanonicalOwner() {
        UUID owner = UUID.randomUUID();

        StepVerifier.create(insertUser(owner)
                        .then(repository.register(owner, "token-register-1", "android"))
                        .flatMapMany(token -> repository.findActiveByOwnerPublicId(owner, 10)
                                .collectList()
                                .map(tokens -> new TokenEvidence(token, tokens))))
                .assertNext(result -> {
                    assertThat(result.token().ownerUserPublicId()).isEqualTo(owner);
                    assertThat(result.token().platform()).isEqualTo("ANDROID");
                    assertThat(result.tokens()).hasSize(1);
                })
                .verifyComplete();
    }

    @Test
    void registeringExistingTokenReactivatesItForNewOwnerWithAudit() {
        UUID firstOwner = UUID.randomUUID();
        UUID secondOwner = UUID.randomUUID();

        StepVerifier.create(insertUser(firstOwner)
                        .then(insertUser(secondOwner))
                        .then(repository.register(firstOwner, "token-reassign-1", "ios"))
                        .flatMap(token -> repository.invalidate("token-reassign-1")
                                .then(repository.register(secondOwner, "token-reassign-1", "web"))))
                .assertNext(token -> {
                    assertThat(token.ownerUserPublicId()).isEqualTo(secondOwner);
                    assertThat(token.platform()).isEqualTo("WEB");
                    assertThat(token.status()).isEqualTo("ACTIVE");
                    assertThat(token.invalidatedAt()).isNull();
                })
                .verifyComplete();
    }

    @Test
    void invalidationIsIdempotentAndRemovesActiveToken() {
        UUID owner = UUID.randomUUID();

        StepVerifier.create(insertUser(owner)
                        .then(repository.register(owner, "token-invalidate-1", "web"))
                        .flatMap(token -> repository.invalidate("token-invalidate-1"))
                        .flatMap(first -> repository.invalidate("token-invalidate-1")
                                .map(second -> new InvalidationEvidence(first, second))))
                .assertNext(result -> {
                    assertThat(result.first()).isTrue();
                    assertThat(result.second()).isFalse();
                })
                .verifyComplete();
    }

    @Test
    void missingOwnerDoesNotRegisterToken() {
        StepVerifier.create(repository.register(UUID.randomUUID(), "token-missing-1", "android"))
                .verifyComplete();
    }

    private reactor.core.publisher.Mono<Void> insertUser(UUID publicId) {
        return databaseClient.sql("""
                        INSERT INTO app_users (public_id, email, role, status)
                        VALUES (:publicId, :email, 'BUYER', 'ACTIVE')
                        """)
                .bind("publicId", publicId)
                .bind("email", "token-owner-" + publicId + "@example.test")
                .fetch().rowsUpdated().then();
    }

    private record TokenEvidence(DeviceTokenRecord token, java.util.List<DeviceTokenRecord> tokens) {
    }

    private record InvalidationEvidence(boolean first, boolean second) {
    }
}
