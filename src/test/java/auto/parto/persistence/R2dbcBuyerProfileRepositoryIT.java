package auto.parto.persistence;

import auto.parto.dto.BuyerDto;
import auto.parto.dto.TokenDto;
import auto.parto.enums.CustomerType;
import auto.parto.mapper.BuyerIdentityMapper;
import auto.parto.service.BuyerIdentityStrategy;
import auto.parto.utils.JwtTokenGenerator;
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

import java.time.Instant;
import java.util.UUID;

import static io.r2dbc.spi.ConnectionFactoryOptions.DATABASE;
import static io.r2dbc.spi.ConnectionFactoryOptions.DRIVER;
import static io.r2dbc.spi.ConnectionFactoryOptions.HOST;
import static io.r2dbc.spi.ConnectionFactoryOptions.PASSWORD;
import static io.r2dbc.spi.ConnectionFactoryOptions.PORT;
import static io.r2dbc.spi.ConnectionFactoryOptions.USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class R2dbcBuyerProfileRepositoryIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private DatabaseClient databaseClient;
    private AppUserRepository appUserRepository;
    private BuyerProfileRepository profileRepository;
    private BuyerIdentityOtpRepository identityRepository;
    private BuyerIdentityStrategy identityStrategy;

    @BeforeAll
    void migrateAndCreateRepositories() {
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
        TransactionalOperator transactions = TransactionalOperator.create(
                new R2dbcTransactionManager(connectionFactory));
        appUserRepository = new R2dbcAppUserRepository(databaseClient, transactions);
        profileRepository = new R2dbcBuyerProfileRepository(databaseClient, transactions);
        identityRepository = new R2dbcBuyerIdentityOtpRepository(databaseClient, transactions, "test-only-pepper");
        JwtTokenGenerator jwtTokenGenerator = mock(JwtTokenGenerator.class);
        when(jwtTokenGenerator.encode(any(String.class), any(BuyerDto.class), eq(CustomerType.BUYER)))
                .thenAnswer(invocation -> Mono.just(TokenDto.<BuyerDto>builder()
                        .token("test-token").user(invocation.getArgument(1)).build()));
        BuyerIdentityMapper mapper = new BuyerIdentityMapper() {
            @Override
            public BuyerDto toDto(BuyerIdentityRecord identity) {
                return BuyerDto.builder().id(identity.publicId().toString()).email(identity.email()).build();
            }
        };
        identityStrategy = new BuyerIdentityStrategy(identityRepository, mapper, jwtTokenGenerator);
    }

    @Test
    void readsAndUpdatesBuyerProfileByCanonicalUserPublicId() {
        UUID userPublicId = UUID.randomUUID();
        AppUserRecord user = new AppUserRecord(userPublicId, null, null,
                "buyer-" + userPublicId + "@example.test", "BUYER", "ACTIVE", "v1", null, null);

        StepVerifier.create(appUserRepository.create(user)
                        .flatMap(created -> insertProfile(created.publicId())
                                .then(profileRepository.findByUserPublicId(created.publicId())))
                        .flatMap(profile -> profileRepository.updateByUserPublicId(
                                profile.userPublicId(), "Ada", "Lovelace", "+212600000000",
                                "https://example.test/avatar")))
                .assertNext(profile -> {
                    assertThat(profile.userPublicId()).isEqualTo(userPublicId);
                    assertThat(profile.firstName()).isEqualTo("Ada");
                    assertThat(profile.lastName()).isEqualTo("Lovelace");
                    assertThat(profile.phoneNumber()).isEqualTo("+212600000000");
                    assertThat(profile.pictureUrl()).isEqualTo("https://example.test/avatar");
                })
                .verifyComplete();
    }

    @Test
    void missingBuyerProfileIsExplicit() {
        StepVerifier.create(profileRepository.findByUserPublicId(UUID.randomUUID()))
                .verifyComplete();
    }

    @Test
    void buyerIdentityOnboardingProducesCanonicalIdForProfileUpdate() {
        String email = "onboarding-" + UUID.randomUUID() + "@example.test";

        StepVerifier.create(identityStrategy.create(auto.parto.dto.OtpRequestDto.builder()
                                .email(email)
                                .firstName("Initial")
                                .lastName("Buyer")
                                .phoneNumber("+212611111111")
                                .customerType(CustomerType.BUYER)
                                .build(), "123456")
                        .cast(BuyerDto.class)
                        .flatMap(created -> identityStrategy.validate("123456", email)
                                .map(TokenDto::getUser)
                                .flatMap(user -> profileRepository.updateByUserPublicId(
                                        UUID.fromString(user.getId()), "Updated", "Buyer",
                                        "+212622222222", "https://example.test/avatar"))))
                .assertNext(profile -> {
                    assertThat(profile.firstName()).isEqualTo("Updated");
                    assertThat(profile.phoneNumber()).isEqualTo("+212622222222");
                    assertThat(profile.pictureUrl()).isEqualTo("https://example.test/avatar");
                })
                .verifyComplete();
    }

    @Test
    void replacingOtpInvalidatesPreviousBuyerChallenge() {
        String email = uniqueEmail();
        Instant expiresAt = Instant.now().plusSeconds(300);

        StepVerifier.create(identityRepository.create(email, "Initial", "Buyer", "+212611111111",
                                "111111", expiresAt)
                        .then(identityRepository.replaceOtp(email, "222222", expiresAt))
                        .then(identityRepository.consumeValidOtp(email, "111111", Instant.now())))
                .expectNext(false)
                .verifyComplete();
        StepVerifier.create(identityRepository.consumeValidOtp(email, "222222", Instant.now()))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void concurrentBuyerConsumptionSucceedsExactlyOnce() {
        String email = uniqueEmail();

        StepVerifier.create(identityRepository.create(email, "Initial", "Buyer", "+212611111111",
                                "123456", Instant.now().plusSeconds(300))
                        .thenMany(reactor.core.publisher.Flux.range(0, 2)
                                .flatMap(ignored -> identityRepository.consumeValidOtp(email, "123456", Instant.now()), 2)
                                .collectList()))
                .assertNext(results -> assertThat(results).containsExactlyInAnyOrder(true, false))
                .verifyComplete();
    }

    @Test
    void failedAttemptsLockBuyerChallengeAtFive() {
        String email = uniqueEmail();

        StepVerifier.create(identityRepository.create(email, "Initial", "Buyer", "+212611111111",
                                "123456", Instant.now().plusSeconds(300))
                        .thenMany(reactor.core.publisher.Flux.range(0, 5)
                                .flatMap(ignored -> identityRepository.recordFailedAttempt(email, Instant.now())))
                        .then(identityRepository.findByEmail(email)))
                .assertNext(identity -> {
                    assertThat(identity.failedAttempts()).isEqualTo(5);
                    assertThat(identity.lockedUntil()).isAfter(Instant.now());
                })
                .verifyComplete();
    }

    @Test
    void concurrentReplacementsLeaveOneActiveBuyerChallenge() {
        String email = uniqueEmail();
        Instant expiresAt = Instant.now().plusSeconds(300);

        StepVerifier.create(identityRepository.create(email, "Initial", "Buyer", "+212611111111",
                                "000000", expiresAt)
                        .thenMany(reactor.core.publisher.Flux.just(
                                        identityRepository.replaceOtp(email, "111111", expiresAt),
                                        identityRepository.replaceOtp(email, "222222", expiresAt))
                                .flatMap(operation -> operation, 2)
                                .collectList())
                        .flatMap(replacements -> databaseClient.sql("""
                                        SELECT count(*) FILTER (WHERE oc.consumed_at IS NULL) AS active,
                                               count(*) FILTER (WHERE oc.consumed_at IS NULL
                                                   AND ar.entity_id = oc.id
                                                   AND ar.id = oc.last_audit_record_id) AS consistent_audits
                                        FROM otp_challenges oc
                                        LEFT JOIN audit_records ar ON ar.id = oc.last_audit_record_id
                                        JOIN app_users au ON au.id = oc.user_id
                                        WHERE au.email = :email AND oc.purpose = 'LOGIN'
                                        """)
                                .bind("email", email)
                                .map((row, metadata) -> new ActiveChallengeCounts(
                                        row.get("active", Long.class), row.get("consistent_audits", Long.class)))
                                .one()
                                .flatMap(counts -> reactor.core.publisher.Mono.zip(
                                        identityRepository.consumeValidOtp(email, "111111", Instant.now()),
                                        identityRepository.consumeValidOtp(email, "222222", Instant.now()))
                                        .map(consumed -> new BuyerConcurrentResult(counts, consumed)))))
                .assertNext(result -> {
                    assertThat(result.counts().active()).isEqualTo(1);
                    assertThat(result.counts().consistentAudits()).isEqualTo(1);
                    assertThat(result.consumed().getT1() ^ result.consumed().getT2()).isTrue();
                })
                .verifyComplete();
    }

    @Test
    void duplicateBuyerIdentityIsRejectedWithoutCreatingAdditionalRows() {
        String email = uniqueEmail();
        Instant expiresAt = Instant.now().plusSeconds(300);

        StepVerifier.create(identityRepository.create(email, "Initial", "Buyer", "+212611111111",
                                "123456", expiresAt))
                .expectNextCount(1)
                .verifyComplete();
        StepVerifier.create(identityRepository.create(email, "Initial", "Buyer", "+212611111111",
                                "654321", expiresAt))
                .expectError()
                .verify();

        StepVerifier.create(databaseClient.sql("""
                        SELECT (SELECT count(*) FROM app_users WHERE email = :email) AS users,
                               (SELECT count(*) FROM buyer_profiles bp JOIN app_users au ON au.id = bp.user_id
                                WHERE au.email = :email) AS profiles,
                               (SELECT count(*) FROM otp_challenges oc JOIN app_users au ON au.id = oc.user_id
                                WHERE au.email = :email) AS otps
                        """)
                .bind("email", email)
                .map((row, metadata) -> new Counts(row.get("users", Long.class),
                        row.get("profiles", Long.class), row.get("otps", Long.class)))
                .one())
                .assertNext(counts -> {
                    assertThat(counts.users()).isEqualTo(1);
                    assertThat(counts.profiles()).isEqualTo(1);
                    assertThat(counts.otps()).isEqualTo(1);
                })
                .verifyComplete();
    }

    @Test
    void failedBuyerProfileInsertRollsBackUserProfileAndOtp() {
        String email = uniqueEmail();
        String oversizedFirstName = "x".repeat(121);

        StepVerifier.create(identityRepository.create(email, oversizedFirstName, "Buyer",
                                "+212611111111", "123456", Instant.now().plusSeconds(300)))
                .expectError()
                .verify();

        StepVerifier.create(databaseClient.sql("""
                        SELECT (SELECT count(*) FROM app_users WHERE email = :email) AS users,
                               (SELECT count(*) FROM buyer_profiles bp JOIN app_users au ON au.id = bp.user_id
                                WHERE au.email = :email) AS profiles,
                               (SELECT count(*) FROM otp_challenges oc JOIN app_users au ON au.id = oc.user_id
                                WHERE au.email = :email) AS otps
                        """)
                .bind("email", email)
                .map((row, metadata) -> new Counts(row.get("users", Long.class),
                        row.get("profiles", Long.class), row.get("otps", Long.class)))
                .one())
                .assertNext(counts -> {
                    assertThat(counts.users()).isZero();
                    assertThat(counts.profiles()).isZero();
                    assertThat(counts.otps()).isZero();
                })
                .verifyComplete();
    }

    private reactor.core.publisher.Mono<Void> insertProfile(UUID userPublicId) {
        return databaseClient.sql("""
                        INSERT INTO buyer_profiles (public_id, user_id, first_name, last_name)
                        SELECT :profilePublicId, id, 'Initial', 'Buyer'
                        FROM app_users
                        WHERE public_id = :userPublicId
                        """)
                .bind("profilePublicId", UUID.randomUUID())
                .bind("userPublicId", userPublicId)
                .fetch()
                .rowsUpdated()
                .then();
    }

    private static String uniqueEmail() {
        return "buyer-" + UUID.randomUUID() + "@example.test";
    }

    private record Counts(Long users, Long profiles, Long otps) {}

    private record ActiveChallengeCounts(Long active, Long consistentAudits) {}

    private record BuyerConcurrentResult(ActiveChallengeCounts counts,
                                         reactor.util.function.Tuple2<Boolean, Boolean> consumed) {}
}
