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
class R2dbcWalletRepositoryIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private DatabaseClient databaseClient;
    private WalletRepository repository;

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
        repository = new R2dbcWalletRepository(databaseClient,
                TransactionalOperator.create(new R2dbcTransactionManager(connectionFactory)));
    }

    @Test
    void mutationCreatesWalletAndAuditedLedgerEntryInMinorUnits() {
        UUID owner = UUID.randomUUID();

        StepVerifier.create(insertUser(owner)
                        .then(repository.mutate(owner, "MAD", 12500, "CREDIT", "credit-1", "deposit-1")))
                .assertNext(result -> {
                    assertThat(result.applied()).isTrue();
                    assertThat(result.wallet().balanceMinor()).isEqualTo(12500);
                    assertThat(result.wallet().currency()).isEqualTo("MAD");
                    assertThat(result.ledgerPublicId()).isNotNull();
                })
                .verifyComplete();
    }

    @Test
    void repeatedIdempotencyKeyDoesNotApplySecondLedgerMutation() {
        UUID owner = UUID.randomUUID();

        StepVerifier.create(insertUser(owner)
                        .then(repository.mutate(owner, "MAD", 1000, "CREDIT", "same-key", null))
                        .flatMap(first -> repository.mutate(owner, "MAD", 1000, "CREDIT", "same-key", null)))
                .assertNext(result -> {
                    assertThat(result.applied()).isFalse();
                    assertThat(result.wallet().balanceMinor()).isEqualTo(1000);
                })
                .verifyComplete();
    }

    @Test
    void insufficientDebitDoesNotChangeWalletOrLedger() {
        UUID owner = UUID.randomUUID();

        StepVerifier.create(insertUser(owner)
                        .then(repository.mutate(owner, "MAD", 500, "CREDIT", "seed", null))
                        .then(repository.mutate(owner, "MAD", -600, "DEBIT", "too-large", null)))
                .expectErrorMatches(error -> error instanceof IllegalStateException
                        && error.getMessage().contains("insufficient"))
                .verify();

        StepVerifier.create(repository.findByOwnerPublicId(owner, "MAD"))
                .assertNext(wallet -> assertThat(wallet.balanceMinor()).isEqualTo(500))
                .verifyComplete();
    }

    @Test
    void missingOwnerDoesNotCreateWallet() {
        UUID missingOwner = UUID.randomUUID();

        StepVerifier.create(repository.mutate(missingOwner, "MAD", 100, "CREDIT", "missing", null))
                .verifyComplete();
    }

    private reactor.core.publisher.Mono<Void> insertUser(UUID publicId) {
        return databaseClient.sql("""
                        INSERT INTO app_users (public_id, email, role, status)
                        VALUES (:publicId, :email, 'SELLER', 'ACTIVE')
                        """)
                .bind("publicId", publicId)
                .bind("email", "wallet-" + publicId + "@example.test")
                .fetch().rowsUpdated().then();
    }
}
