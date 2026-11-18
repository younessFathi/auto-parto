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
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.UUID;

import static io.r2dbc.spi.ConnectionFactoryOptions.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class R2dbcWalletFundingRepositoryIT {
    @Container private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    private DatabaseClient database;
    private TransactionalOperator transactions;
    private WalletRepository wallets;
    private WalletFundingRepository funding;

    @BeforeAll
    void setup() {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").load().migrate();
        var factory = io.r2dbc.spi.ConnectionFactories.get(ConnectionFactoryOptions.builder()
                .option(DRIVER, "postgresql").option(HOST, POSTGRES.getHost())
                .option(PORT, POSTGRES.getFirstMappedPort()).option(USER, POSTGRES.getUsername())
                .option(PASSWORD, POSTGRES.getPassword()).option(DATABASE, POSTGRES.getDatabaseName()).build());
        database = DatabaseClient.create(factory);
        transactions = TransactionalOperator.create(new R2dbcTransactionManager(factory));
        wallets = new R2dbcWalletRepository(database, transactions);
        funding = new R2dbcWalletFundingRepository(database, wallets, transactions);
    }

    @Test
    void recordsFundingAndActualAdministratorAuditWithLedgerCorrelation() {
        UUID admin = user("ADMIN", "ACTIVE"), owner = user("SELLER", "ACTIVE");
        var result = fund(admin, owner, "100", 12500, "key").block();
        assertThat(result.balanceAfterMinor()).isEqualTo(12500);
        assertThat(result.amountMinor()).isEqualTo(12500);
        assertThat(countFunding("100")).isEqualTo(1);
        Long audits = database.sql("""
                SELECT count(*) AS total FROM wallet_funding_events event
                JOIN audit_records audit ON audit.id = event.audit_record_id
                JOIN app_users actor ON actor.id = audit.actor_user_id
                JOIN wallet_ledger ledger ON ledger.public_id = event.ledger_public_id
                WHERE event.funding_reference = '100' AND actor.public_id = :admin
                  AND audit.entity_type = 'wallet_funding' AND audit.entity_id = event.id
                  AND ledger.amount_minor = event.amount_minor
                """).bind("admin", admin).map((row, meta) -> row.get("total", Long.class)).one().block();
        assertThat(audits).isEqualTo(1);
    }

    @Test
    void permanentResultSurvivesOtherMutationsDifferentKeysExpiryAndNewRepositoryInstance() {
        UUID admin = user("ADMIN", "ACTIVE"), owner = user("SELLER", "ACTIVE");
        String reference = reference();
        var first = fund(admin, owner, reference, 100, "initial").block();
        fund(admin, owner, reference(), 50, "other").block();
        database.sql("UPDATE idempotency_records SET expires_at = CURRENT_TIMESTAMP - INTERVAL '1 minute'")
                .fetch().rowsUpdated().block();
        wallets.purgeExpiredIdempotencyRecords(Instant.now(), 1000).block();
        var restarted = new R2dbcWalletFundingRepository(database, new R2dbcWalletRepository(database, transactions), transactions);
        var replay = restarted.confirm(admin, owner, 100, "MAD", reference, "different").block();
        assertThat(replay).isEqualTo(first);
        assertThat(wallets.findByOwnerPublicId(owner, "MAD").block().balanceMinor()).isEqualTo(150);
        assertThat(countLedger(owner)).isEqualTo(2);
    }

    @Test
    void fundingReferenceIsGlobalAcrossOwnersAmountsAndCurrencies() {
        UUID admin = user("ADMIN", "ACTIVE"), owner = user("SELLER", "ACTIVE"), other = user("SELLER", "ACTIVE");
        String ref = reference();
        fund(admin, owner, ref, 100, "first").block();
        StepVerifier.create(fund(admin, other, ref, 100, "second")).expectError(ConflictException.class).verify();
        StepVerifier.create(fund(admin, owner, ref, 101, "third")).expectError(ConflictException.class).verify();
        StepVerifier.create(funding.confirm(admin, owner, 100, "EUR", ref, "fourth"))
                .expectError(ConflictException.class).verify();
        assertThat(countLedger(owner)).isEqualTo(1);
        assertThat(countLedger(other)).isZero();
    }

    @Test
    void concurrentFundingWithDifferentRequestKeysCommitsOnlyOnce() {
        UUID admin = user("ADMIN", "ACTIVE"), owner = user("SELLER", "ACTIVE");
        String ref = reference();
        var results = Flux.range(0, 5).flatMap(i -> fund(admin, owner, ref, 100, "concurrent-" + i), 5)
                .collectList().block();
        assertThat(results).hasSize(5).allMatch(result -> result.equals(results.getFirst()));
        assertThat(countLedger(owner)).isEqualTo(1);
        assertThat(countFunding(ref)).isEqualTo(1);
    }

    @Test
    void incompatibleConcurrentClaimsForOneGlobalReferenceCannotBothCredit() {
        UUID admin = user("ADMIN", "ACTIVE"), firstOwner = user("SELLER", "ACTIVE"), secondOwner = user("SELLER", "ACTIVE");
        String ref = reference();
        var signals = Flux.merge(funding.confirm(admin, firstOwner, 100, "MAD", ref, "first").materialize(),
                        funding.confirm(admin, secondOwner, 100, "EUR", ref, "second").materialize())
                .collectList().block();
        assertThat(signals.stream().filter(reactor.core.publisher.Signal::isOnNext).count()).isEqualTo(1);
        assertThat(signals.stream().filter(reactor.core.publisher.Signal::isOnError).count()).isEqualTo(1);
        assertThat(countLedger(firstOwner) + countLedger(secondOwner)).isEqualTo(1);
        assertThat(countFunding(ref)).isEqualTo(1);
    }

    @Test
    void persistedRoleAndStatusAreRecheckedForEveryRequest() {
        UUID admin = user("ADMIN", "ACTIVE"), inactive = user("ADMIN", "INACTIVE");
        UUID seller = user("SELLER", "ACTIVE"), disabled = user("SELLER", "INACTIVE"), buyer = user("BUYER", "ACTIVE");
        for (UUID invalidAdmin : java.util.List.of(inactive, seller, buyer, UUID.randomUUID())) {
            StepVerifier.create(fund(invalidAdmin, seller, reference(), 100, "key"))
                    .expectErrorMatches(error -> status(error, 403)).verify();
        }
        for (UUID invalidOwner : java.util.List.of(disabled, buyer, UUID.randomUUID())) {
            StepVerifier.create(fund(admin, invalidOwner, reference(), 100, "key"))
                    .expectErrorMatches(error -> status(error, 404)).verify();
        }
        assertThat(countLedger(seller)).isZero();
    }

    @Test
    void requestKeyCannotBeReassignedToAnotherFundingReference() {
        UUID admin = user("ADMIN", "ACTIVE"), owner = user("SELLER", "ACTIVE");
        String first = reference(), second = reference();
        fund(admin, owner, first, 100, "shared-key").block();
        StepVerifier.create(fund(admin, owner, second, 100, "shared-key"))
                .expectError(ConflictException.class).verify();
        assertThat(countFunding(second)).isZero();
        assertThat(countLedger(owner)).isEqualTo(1);
    }

    @Test
    void overflowRollsBackFundingEvidenceAndAnyNewAudit() {
        UUID admin = user("ADMIN", "ACTIVE"), owner = user("SELLER", "ACTIVE");
        wallets.mutate(owner, "MAD", Long.MAX_VALUE, "CREDIT", "seed", "fixture").block();
        String ref = reference();
        StepVerifier.create(fund(admin, owner, ref, 1, "overflow")).expectError(IllegalArgumentException.class).verify();
        assertThat(countFunding(ref)).isZero();
        assertThat(countLedger(owner)).isEqualTo(1);
        assertThat(wallets.findByOwnerPublicId(owner, "MAD").block().balanceMinor()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void completionFailureRollsBackCreditAndRetryCanCompleteOnce() {
        UUID admin = user("ADMIN", "ACTIVE"), owner = user("SELLER", "ACTIVE");
        String ref = "failure-" + reference();
        database.sql("""
                CREATE FUNCTION fail_funding_completion_test() RETURNS trigger AS $$
                BEGIN
                    IF NEW.funding_reference LIKE 'failure-%' THEN RAISE EXCEPTION 'test failure'; END IF;
                    RETURN NEW;
                END; $$ LANGUAGE plpgsql
                """).fetch().rowsUpdated().block();
        database.sql("CREATE TRIGGER fail_funding_completion_test BEFORE UPDATE ON wallet_funding_events "
                + "FOR EACH ROW EXECUTE FUNCTION fail_funding_completion_test()")
                .fetch().rowsUpdated().block();
        try {
            StepVerifier.create(fund(admin, owner, ref, 100, "retry-key")).expectError().verify();
            assertThat(countFunding(ref)).isZero();
            assertThat(countLedger(owner)).isZero();
            Long fundingAudits = database.sql("SELECT count(*) AS total FROM audit_records audit "
                            + "JOIN app_users actor ON actor.id = audit.actor_user_id "
                            + "WHERE audit.entity_type = 'wallet_funding' AND actor.public_id = :admin")
                    .bind("admin", admin).map((row, meta) -> row.get("total", Long.class)).one().block();
            assertThat(fundingAudits).isZero();
            assertThat(wallets.findByOwnerPublicId(owner, "MAD").block()).isNull();
        } finally {
            database.sql("DROP TRIGGER fail_funding_completion_test ON wallet_funding_events").fetch().rowsUpdated().block();
            database.sql("DROP FUNCTION fail_funding_completion_test()").fetch().rowsUpdated().block();
        }
        fund(admin, owner, ref, 100, "retry-key").block();
        assertThat(countLedger(owner)).isEqualTo(1);
    }

    @Test
    void completedFundingCannotBeChangedOrDeleted() {
        UUID admin = user("ADMIN", "ACTIVE"), owner = user("SELLER", "ACTIVE");
        String ref = reference();
        fund(admin, owner, ref, 100, "immutable").block();
        StepVerifier.create(database.sql("UPDATE wallet_funding_events SET amount_minor = 200 WHERE funding_reference = :ref")
                .bind("ref", ref).fetch().rowsUpdated()).expectError().verify();
        StepVerifier.create(database.sql("DELETE FROM wallet_funding_events WHERE funding_reference = :ref")
                .bind("ref", ref).fetch().rowsUpdated()).expectError().verify();
        assertThat(countFunding(ref)).isEqualTo(1);
    }

    @Test
    void invalidAmountsReferencesAndCurrencyFailBeforePersistence() {
        UUID admin = UUID.randomUUID(), owner = UUID.randomUUID();
        assertThatThrownBy(() -> funding.confirm(admin, owner, 0, "MAD", "proof", "key"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> funding.confirm(admin, owner, 1, "XXX", "proof", "key"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> funding.confirm(admin, owner, 1, "MAD", " ", "key"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private UUID user(String role, String status) {
        UUID id = UUID.randomUUID();
        database.sql("INSERT INTO app_users (public_id, email, role, status) VALUES (:id, :email, :role, :status)")
                .bind("id", id).bind("email", id + "@example.test").bind("role", role).bind("status", status)
                .fetch().rowsUpdated().block();
        return id;
    }

    private Mono<WalletFundingResult> fund(UUID admin, UUID owner, String ref, long amount, String key) {
        return funding.confirm(admin, owner, amount, "MAD", ref, key);
    }

    private long countFunding(String ref) {
        return database.sql("SELECT count(*) AS total FROM wallet_funding_events WHERE funding_reference = :ref")
                .bind("ref", ref).map((row, meta) -> row.get("total", Long.class)).one().block();
    }

    private long countLedger(UUID owner) {
        return database.sql("SELECT count(*) AS total FROM wallet_ledger ledger JOIN wallets wallet ON wallet.id = ledger.wallet_id "
                + "JOIN app_users owner ON owner.id = wallet.owner_user_id WHERE owner.public_id = :owner")
                .bind("owner", owner).map((row, meta) -> row.get("total", Long.class)).one().block();
    }

    private String reference() { return UUID.randomUUID().toString(); }
    private boolean status(Throwable error, int expected) {
        return error instanceof ResponseStatusException response && response.getStatusCode().value() == expected;
    }
}
