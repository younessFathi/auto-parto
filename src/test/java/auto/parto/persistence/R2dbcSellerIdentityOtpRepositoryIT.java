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

import java.time.Instant;
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
class R2dbcSellerIdentityOtpRepositoryIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private DatabaseClient databaseClient;
    private SellerIdentityOtpRepository repository;
    private SellerProfileRepository profileRepository;

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
        repository = new R2dbcSellerIdentityOtpRepository(
                databaseClient,
                TransactionalOperator.create(new R2dbcTransactionManager(connectionFactory)),
                UUID.randomUUID().toString());
        profileRepository = new R2dbcSellerProfileRepository(
                databaseClient,
                TransactionalOperator.create(new R2dbcTransactionManager(connectionFactory)));
    }

    @Test
    void createsIdentityHashesOtpAndConsumesItWithAudit() {
        String email = uniqueEmail();
        Instant expiresAt = Instant.now().plusSeconds(300);

        StepVerifier.create(repository.create(email, "Ada", "Lovelace", "123456", expiresAt)
                        .flatMap(identity -> repository.consumeValidOtp(email, "123456", Instant.now())
                                .map(consumed -> new Result(identity, consumed))))
                .assertNext(result -> {
                    assertThat(result.identity().email()).isEqualTo(email);
                    assertThat(result.identity().firstName()).isEqualTo("Ada");
                    assertThat(result.identity().active()).isTrue();
                    assertThat(result.identity().otpHash()).isNotEqualTo("123456");
                    assertThat(result.identity().otpHash()).hasSize(43);
                    assertThat(result.consumed()).isTrue();
                })
                .verifyComplete();

        StepVerifier.create(repository.consumeValidOtp(email, "123456", Instant.now()))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void wrongOtpRecordsFailedAttempt() {
        String email = uniqueEmail();

        StepVerifier.create(repository.create(email, "Grace", "Hopper", "123456",
                                Instant.now().plusSeconds(300))
                        .then(repository.consumeValidOtp(email, "wrong", Instant.now()))
                        .flatMap(ignored -> repository.recordFailedAttempt(email, Instant.now()))
                        .then(repository.findByEmail(email)))
                .assertNext(identity -> assertThat(identity.failedAttempts()).isEqualTo(1))
                .verifyComplete();
    }

    @Test
    void replacingOtpInvalidatesThePreviousChallenge() {
        String email = uniqueEmail();
        Instant expiresAt = Instant.now().plusSeconds(300);

        StepVerifier.create(repository.create(email, "Katherine", "Johnson", "111111", expiresAt)
                        .then(repository.replaceOtp(email, "222222", expiresAt))
                        .then(repository.consumeValidOtp(email, "111111", Instant.now())))
                .expectNext(false)
                .verifyComplete();
        StepVerifier.create(repository.consumeValidOtp(email, "222222", Instant.now()))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void profileRepositoryReadsAndUpdatesSellerProfileWithoutExposingInternalIds() {
        String email = uniqueEmail();

        StepVerifier.create(repository.create(email, "Ada", "Lovelace", "111111",
                                Instant.now().plusSeconds(300))
                        .flatMap(identity -> profileRepository.findByEmail(email)
                                .flatMap(profile -> profileRepository.findByUserPublicId(profile.userPublicId())
                                        .flatMap(found -> profileRepository.updateByUserPublicId(
                                                found.userPublicId(), "Grace", "Hopper", "+212600000000",
                                                "ENGINE", "Computing")))))
                .assertNext(profile -> {
                    assertThat(profile.firstName()).isEqualTo("Grace");
                    assertThat(profile.lastName()).isEqualTo("Hopper");
                    assertThat(profile.phoneNumber()).isEqualTo("+212600000000");
                    assertThat(profile.partType()).isEqualTo("ENGINE");
                    assertThat(profile.description()).isEqualTo("Computing");
                    assertThat(profile.publicId()).isNotNull();
                    assertThat(profile.userPublicId()).isNotNull();
                })
                .verifyComplete();
    }

    @Test
    void profileRepositoryReturnsEmptyForUnknownSeller() {
        StepVerifier.create(profileRepository.findByEmail(uniqueEmail()))
                .verifyComplete();
    }

    @Test
    void replacingExpiredOtpDoesNotLeaveAnIndexedChallengeBlockingReplacement() {
        String email = uniqueEmail();
        Instant expiredAt = Instant.now().minusSeconds(1);
        Instant replacementExpiresAt = Instant.now().plusSeconds(300);

        StepVerifier.create(repository.create(email, "James", "Harris", "333333", expiredAt)
                        .then(repository.replaceOtp(email, "444444", replacementExpiresAt))
                        .then(repository.consumeValidOtp(email, "444444", Instant.now())))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void failedAttemptsLockChallengeAtFive() {
        String email = uniqueEmail();

        StepVerifier.create(repository.create(email, "Dorothy", "Vaughan", "123456",
                                Instant.now().plusSeconds(300))
                        .thenMany(reactor.core.publisher.Flux.range(0, 5)
                                .flatMap(ignored -> repository.recordFailedAttempt(email, Instant.now())))
                        .then(repository.findByEmail(email)))
                .assertNext(identity -> {
                    assertThat(identity.failedAttempts()).isEqualTo(5);
                    assertThat(identity.lockedUntil()).isAfter(Instant.now());
                })
                .verifyComplete();
    }

    @Test
    void concurrentValidConsumesSucceedExactlyOnce() {
        String email = uniqueEmail();

        StepVerifier.create(repository.create(email, "Mary", "Jackson", "123456",
                                Instant.now().plusSeconds(300))
                        .thenMany(reactor.core.publisher.Flux.range(0, 2)
                                .flatMap(ignored -> repository.consumeValidOtp(email, "123456", Instant.now()), 2)
                                .collectList()))
                .assertNext(results -> assertThat(results).containsExactlyInAnyOrder(true, false))
                .verifyComplete();
    }

    @Test
    void concurrentReplacementsLeaveOneActiveChallengeAndOneUsableCode() {
        String email = uniqueEmail();
        Instant expiresAt = Instant.now().plusSeconds(300);

        StepVerifier.create(repository.create(email, "James", "Harris", "000000", expiresAt)
                        .thenMany(reactor.core.publisher.Flux.just(
                                        repository.replaceOtp(email, "111111", expiresAt),
                                        repository.replaceOtp(email, "222222", expiresAt))
                                .flatMap(identity -> identity, 2)
                                .collectList())
                        .flatMap(replacements -> databaseClient.sql("""
                                        SELECT
                                            count(*) FILTER (WHERE oc.consumed_at IS NULL) AS active,
                                            count(*) FILTER (
                                                WHERE oc.consumed_at IS NULL
                                                  AND ar.entity_id = oc.id
                                                  AND ar.id = oc.last_audit_record_id
                                            ) AS consistent_audits
                                        FROM otp_challenges oc
                                        LEFT JOIN audit_records ar ON ar.id = oc.last_audit_record_id
                                        JOIN app_users au ON au.id = oc.user_id
                                        WHERE au.email = :email AND oc.purpose = 'LOGIN'
                                        """)
                                .bind("email", email)
                                .map((row, metadata) -> new ActiveChallengeCounts(
                                        row.get("active", Long.class),
                                        row.get("consistent_audits", Long.class)))
                                .one()
                                .flatMap(counts -> reactor.core.publisher.Mono.zip(
                                        repository.consumeValidOtp(email, "111111", Instant.now()),
                                        repository.consumeValidOtp(email, "222222", Instant.now()))
                                        .map(consumed -> new ConcurrentResult(counts, consumed)))))
                .assertNext(result -> {
                    assertThat(result.counts().active()).isEqualTo(1);
                    assertThat(result.counts().consistentAudits()).isEqualTo(1);
                    assertThat(result.consumed().getT1() ^ result.consumed().getT2()).isTrue();
                })
                .verifyComplete();
    }

    @Test
    void duplicateIdentityRollsBackProfileAndOtpInsert() {
        String email = uniqueEmail();
        Instant expiresAt = Instant.now().plusSeconds(300);

        StepVerifier.create(repository.create(email, "Alan", "Turing", "123456", expiresAt))
                .expectNextCount(1)
                .verifyComplete();
        StepVerifier.create(repository.create(email, "Alan", "Turing", "654321", expiresAt))
                .expectError()
                .verify();

        StepVerifier.create(databaseClient.sql("""
                        SELECT
                            (SELECT count(*) FROM app_users WHERE email = :email) AS users,
                            (SELECT count(*) FROM seller_profiles sp JOIN app_users au ON au.id = sp.user_id
                             WHERE au.email = :email) AS profiles,
                            (SELECT count(*) FROM otp_challenges oc JOIN app_users au ON au.id = oc.user_id
                             WHERE au.email = :email) AS otps
                        """)
                        .bind("email", email)
                        .map((row, metadata) -> new Counts(
                                row.get("users", Long.class),
                                row.get("profiles", Long.class),
                                row.get("otps", Long.class)))
                        .one())
                .assertNext(counts -> {
                    assertThat(counts.users()).isEqualTo(1);
                    assertThat(counts.profiles()).isEqualTo(1);
                    assertThat(counts.otps()).isEqualTo(1);
                })
                .verifyComplete();
    }

    @Test
    void missingIdentityReturnsEmpty() {
        StepVerifier.create(repository.findByEmail("missing-" + UUID.randomUUID() + "@example.test"))
                .verifyComplete();
    }

    private static String uniqueEmail() {
        return "seller-" + UUID.randomUUID() + "@example.test";
    }

    private record Result(SellerIdentityRecord identity, boolean consumed) {}

    private record Counts(Long users, Long profiles, Long otps) {}

    private record ActiveChallengeCounts(Long active, Long consistentAudits) {}

    private record ConcurrentResult(ActiveChallengeCounts counts,
                                    reactor.util.function.Tuple2<Boolean, Boolean> consumed) {}
}
