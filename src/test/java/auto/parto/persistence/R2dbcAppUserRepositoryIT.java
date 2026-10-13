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
import reactor.core.publisher.Mono;
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
class R2dbcAppUserRepositoryIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private DatabaseClient databaseClient;
    private AppUserRepository repository;

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
        repository = new R2dbcAppUserRepository(
                databaseClient,
                TransactionalOperator.create(new R2dbcTransactionManager(connectionFactory)));
    }

    @Test
    void createsAndFindsUserByPublicIdentityReactively() {
        UUID publicId = UUID.randomUUID();
        AppUserRecord requested = new AppUserRecord(
                publicId, null, null, "buyer-" + publicId + "@example.test",
                "BUYER", "ACTIVE", "v1", null, null);

        StepVerifier.create(repository.create(requested)
                        .flatMap(created -> databaseClient.sql(
                                        "SELECT created_audit_record_id FROM app_users WHERE public_id = :publicId")
                                .bind("publicId", created.publicId())
                                .map((row, metadata) -> row.get("created_audit_record_id", Long.class))
                                .one()
                                .doOnNext(auditId -> assertThat(auditId).isPositive())
                                .then(repository.findByEmail(requested.email()))))
                .assertNext(found -> {
                    assertThat(found.publicId()).isEqualTo(publicId);
                    assertThat(found.email()).isEqualTo(requested.email());
                    assertThat(found.role()).isEqualTo("BUYER");
                })
                .verifyComplete();
    }

    @Test
    void missingIdentityReturnsEmptyResult() {
        StepVerifier.create(repository.findByPublicId(UUID.randomUUID()))
                .verifyComplete();
    }

    @Test
    void findsUserByProviderSubjectAndReturnsEmptyForUnknownSubject() {
        UUID publicId = UUID.randomUUID();
        AppUserRecord requested = new AppUserRecord(
                publicId, "clerk", "user_" + publicId, "clerk-" + publicId + "@example.test",
                "BUYER", "ACTIVE", "v1", null, null);

        StepVerifier.create(repository.create(requested)
                        .then(repository.findByProviderSubject("clerk", requested.providerSubject())))
                .assertNext(found -> {
                    assertThat(found.publicId()).isEqualTo(publicId);
                    assertThat(found.provider()).isEqualTo("clerk");
                    assertThat(found.providerSubject()).isEqualTo(requested.providerSubject());
                })
                .verifyComplete();

        StepVerifier.create(repository.findByProviderSubject("clerk", "unknown-" + publicId))
                .verifyComplete();
    }

    @Test
    void createRollsBackWhenDatabaseRejectsInvalidRole() {
        UUID publicId = UUID.randomUUID();
        AppUserRecord invalid = new AppUserRecord(
                publicId, null, null, "invalid-" + publicId + "@example.test",
                "NOT_A_ROLE", "ACTIVE", "v1", null, null);

        StepVerifier.create(countAuditRecords()
                        .flatMap(before -> repository.create(invalid)
                                .then(Mono.<RollbackEvidence>error(
                                        new AssertionError("invalid role was accepted")))
                                .onErrorResume(error -> countAuditRecords()
                                        .zipWith(repository.findByPublicId(publicId).hasElement())
                                        .map(after -> new RollbackEvidence(
                                                before,
                                                after.getT1(),
                                                after.getT2())))))
                .assertNext(evidence -> {
                    assertThat(evidence.auditCountAfter()).isEqualTo(evidence.auditCountBefore());
                    assertThat(evidence.userPresent()).isFalse();
                })
                .verifyComplete();
    }

    private reactor.core.publisher.Mono<Long> countAuditRecords() {
        return databaseClient.sql("SELECT count(*) AS count FROM audit_records")
                .map((row, metadata) -> row.get("count", Long.class))
                .one();
    }

    private record RollbackEvidence(long auditCountBefore, long auditCountAfter, boolean userPresent) {
    }
}
