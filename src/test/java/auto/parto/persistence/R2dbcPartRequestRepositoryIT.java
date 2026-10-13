package auto.parto.persistence;

import auto.parto.enums.Category;
import auto.parto.enums.Condition;
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
class R2dbcPartRequestRepositoryIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private DatabaseClient databaseClient;
    private PartRequestRepository repository;
    private UUID buyerPublicId;

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
        repository = new R2dbcPartRequestRepository(databaseClient,
                TransactionalOperator.create(new R2dbcTransactionManager(connectionFactory)));
    }

    @Test
    void createsAndReadsRequestByCanonicalPublicIds() {
        buyerPublicId = UUID.randomUUID();

        StepVerifier.create(insertBuyer(buyerPublicId)
                        .then(repository.create(buyerPublicId, "Brake pad", "Front axle",
                                Condition.NEW.name(), Category.CAR.name())))
                .assertNext(request -> {
                    assertThat(request.publicId()).isNotNull();
                    assertThat(request.buyerUserPublicId()).isEqualTo(buyerPublicId);
                    assertThat(request.name()).isEqualTo("Brake pad");
                    assertThat(request.condition()).isEqualTo(Condition.NEW);
                    assertThat(request.category()).isEqualTo(Category.CAR);
                    assertThat(request.status()).isEqualTo("OPEN");
                })
                .verifyComplete();
    }

    @Test
    void listAndCountAreBoundedByBuyerAndPage() {
        UUID buyer = UUID.randomUUID();

        StepVerifier.create(insertBuyer(buyer)
                        .then(repository.create(buyer, "Request one", null, null, Category.TRUCK.name()))
                        .then(repository.create(buyer, "Request two", null, Condition.USED.name(), Category.CAR.name()))
                        .thenMany(repository.findByBuyerPublicId(buyer, 0, 1))
                        .collectList()
                        .flatMap(requests -> repository.countByBuyerPublicId(buyer)
                                .map(count -> new ListingEvidence(requests, count))))
                .assertNext(result -> {
                    assertThat(result.requests()).hasSize(1);
                    assertThat(result.count()).isEqualTo(2);
                })
                .verifyComplete();
    }

    @Test
    void statusUpdateUsesOwnerAndExpectedVersion() {
        UUID buyer = UUID.randomUUID();

        StepVerifier.create(insertBuyer(buyer)
                        .then(repository.create(buyer, "Closable request", null,
                                Condition.USED.name(), Category.CAR.name()))
                        .flatMap(request -> repository.updateStatus(request.publicId(), buyer,
                                "CLOSED", 0, java.time.Instant.now())))
                .assertNext(request -> {
                    assertThat(request.status()).isEqualTo("CLOSED");
                    assertThat(request.updatedAt()).isNotNull();
                })
                .verifyComplete();
    }

    @Test
    void missingPublicIdReturnsEmpty() {
        StepVerifier.create(repository.findByPublicId(UUID.randomUUID()))
                .verifyComplete();
    }

    private reactor.core.publisher.Mono<Void> insertBuyer(UUID publicId) {
        return databaseClient.sql("""
                        INSERT INTO app_users (public_id, email, role, status)
                        VALUES (:publicId, :email, 'BUYER', 'ACTIVE')
                        """)
                .bind("publicId", publicId)
                .bind("email", "buyer-" + publicId + "@example.test")
                .fetch().rowsUpdated().then();
    }

    private record ListingEvidence(java.util.List<PartRequestRecord> requests, long count) {
    }
}
