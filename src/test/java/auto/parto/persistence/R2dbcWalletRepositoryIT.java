package auto.parto.persistence;

import auto.parto.exception.ConflictException;
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
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.UUID;

import static io.r2dbc.spi.ConnectionFactoryOptions.DATABASE;
import static io.r2dbc.spi.ConnectionFactoryOptions.DRIVER;
import static io.r2dbc.spi.ConnectionFactoryOptions.HOST;
import static io.r2dbc.spi.ConnectionFactoryOptions.PASSWORD;
import static io.r2dbc.spi.ConnectionFactoryOptions.PORT;
import static io.r2dbc.spi.ConnectionFactoryOptions.USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void conflictingIdempotencyReuseDoesNotApplyAnotherMutation() {
        UUID owner = UUID.randomUUID();

        StepVerifier.create(insertUser(owner)
                        .then(repository.mutate(owner, "MAD", 1000, "CREDIT", "same-key", null))
                        .then(repository.mutate(owner, "MAD", 2000, "CREDIT", "same-key", null)))
                .expectError(ConflictException.class)
                .verify();

        StepVerifier.create(repository.findByOwnerPublicId(owner, "MAD"))
                .assertNext(wallet -> assertThat(wallet.balanceMinor()).isEqualTo(1000))
                .verifyComplete();
    }

    @Test
    void nullAndLiteralNullExternalReferencesAreDifferentRequests() {
        UUID owner = UUID.randomUUID();

        StepVerifier.create(insertUser(owner)
                        .then(repository.mutate(owner, "MAD", 1000, "CREDIT", "nullable-key", null))
                        .then(repository.mutate(owner, "MAD", 1000, "CREDIT", "nullable-key", "<null>")))
                .expectError(ConflictException.class)
                .verify();

        StepVerifier.create(repository.findByOwnerPublicId(owner, "MAD"))
                .assertNext(wallet -> assertThat(wallet.balanceMinor()).isEqualTo(1000))
                .verifyComplete();
        StepVerifier.create(countLedger(owner))
                .expectNext(1L)
                .verifyComplete();
    }

    @Test
    void concurrentWithdrawalsCannotOverdrawLockedWallet() {
        UUID owner = UUID.randomUUID();

        StepVerifier.create(insertUser(owner)
                        .then(repository.mutate(owner, "MAD", 500, "CREDIT", "seed-concurrent", null))
                        .then(Flux.mergeDelayError(2,
                                        repository.mutate(owner, "MAD", -400, "DEBIT", "withdraw-a", null).materialize(),
                                        repository.mutate(owner, "MAD", -400, "DEBIT", "withdraw-b", null).materialize())
                                .collectList()))
                .assertNext(signals -> {
                    assertThat(signals).hasSize(2);
                    assertThat(signals.stream().filter(signal -> signal.isOnNext())).hasSize(1);
                    assertThat(signals.stream().filter(signal -> signal.isOnError())).hasSize(1);
                })
                .verifyComplete();

        StepVerifier.create(repository.findByOwnerPublicId(owner, "MAD"))
                .assertNext(wallet -> assertThat(wallet.balanceMinor()).isEqualTo(100))
                .verifyComplete();
        StepVerifier.create(countLedger(owner))
                .expectNext(2L)
                .verifyComplete();
    }

    @Test
    void expiredIdempotencyRecordsCanBePurgedBeforeKeyReuse() {
        UUID owner = UUID.randomUUID();

        StepVerifier.create(insertUser(owner)
                        .then(repository.mutate(owner, "MAD", 100, "CREDIT", "retained-key", null))
                        .then(expireIdempotencyRecord(owner, "retained-key"))
                        .then(repository.purgeExpiredIdempotencyRecords(Instant.now(), 100)
                                .doOnNext(deleted -> assertThat(deleted).isEqualTo(1L))
                                .then())
                        .then(repository.mutate(owner, "MAD", 200, "CREDIT", "retained-key", null)))
                .assertNext(result -> {
                    assertThat(result.applied()).isTrue();
                    assertThat(result.wallet().balanceMinor()).isEqualTo(300);
                })
                .verifyComplete();
    }

    @Test
    void walletMutationRequiresAnIdempotencyKey() {
        assertThatThrownBy(() -> repository.mutate(UUID.randomUUID(), "MAD", 100,
                "CREDIT", " ", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("idempotencyKey");
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

    @Test
    void unsupportedCurrencyIsRejectedBeforePersistence() {
        assertThatThrownBy(() -> repository.mutate(UUID.randomUUID(), "XXX", 100,
                "CREDIT", "unsupported", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported currency");
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

    private reactor.core.publisher.Mono<Void> expireIdempotencyRecord(UUID owner, String key) {
        return databaseClient.sql("""
                        UPDATE idempotency_records SET expires_at = CURRENT_TIMESTAMP - INTERVAL '1 minute'
                        WHERE owner_user_id = (SELECT id FROM app_users WHERE public_id = :ownerPublicId)
                          AND idempotency_key = :idempotencyKey
                        """)
                .bind("ownerPublicId", owner)
                .bind("idempotencyKey", key)
                .fetch().rowsUpdated().then();
    }

    private reactor.core.publisher.Mono<Long> countLedger(UUID owner) {
        return databaseClient.sql("""
                        SELECT count(*) AS ledger_count
                        FROM wallet_ledger ledger
                        JOIN wallets wallet ON wallet.id = ledger.wallet_id
                        JOIN app_users owner ON owner.id = wallet.owner_user_id
                        WHERE owner.public_id = :ownerPublicId
                        """)
                .bind("ownerPublicId", owner)
                .map((row, metadata) -> row.get("ledger_count", Long.class))
                .one();
    }
}
