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
class R2dbcPartListingRepositoryIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private DatabaseClient databaseClient;
    private PartListingRepository repository;

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
        repository = new R2dbcPartListingRepository(databaseClient,
                TransactionalOperator.create(new R2dbcTransactionManager(connectionFactory)));
    }

    @Test
    void createsAndReadsListingByCanonicalPublicIds() {
        UUID seller = UUID.randomUUID();

        StepVerifier.create(insertSeller(seller)
                        .then(repository.create(seller, "Brake pad", "Front axle",
                                Condition.NEW.name(), Category.CAR.name(), 12500, "MAD")))
                .assertNext(listing -> {
                    assertThat(listing.publicId()).isNotNull();
                    assertThat(listing.sellerUserPublicId()).isEqualTo(seller);
                    assertThat(listing.amountMinor()).isEqualTo(12500);
                    assertThat(listing.currency()).isEqualTo("MAD");
                    assertThat(listing.status()).isEqualTo("DRAFT");
                })
                .verifyComplete();
    }

    @Test
    void listAndCountAreBoundedBySellerAndPage() {
        UUID seller = UUID.randomUUID();

        StepVerifier.create(insertSeller(seller)
                        .then(repository.create(seller, "Listing one", null, null,
                                Category.TRUCK.name(), 1000, "MAD"))
                        .then(repository.create(seller, "Listing two", null, Condition.USED.name(),
                                Category.CAR.name(), 2000, "MAD"))
                        .thenMany(repository.findBySellerPublicId(seller, 0, 1))
                        .collectList()
                        .flatMap(listings -> repository.countBySellerPublicId(seller)
                                .map(count -> new ListingEvidence(listings, count))))
                .assertNext(result -> {
                    assertThat(result.listings()).hasSize(1);
                    assertThat(result.count()).isEqualTo(2);
                })
                .verifyComplete();
    }

    @Test
    void statusUpdateRequiresOwnerAndExpectedVersion() {
        UUID seller = UUID.randomUUID();

        StepVerifier.create(insertSeller(seller)
                        .then(repository.create(seller, "Closable listing", null,
                                Condition.USED.name(), Category.CAR.name(), 3000, "MAD"))
                        .flatMap(listing -> repository.updateStatus(listing.publicId(), seller,
                                "PUBLISHED", 0, Instant.now())))
                .assertNext(listing -> assertThat(listing.status()).isEqualTo("PUBLISHED"))
                .verifyComplete();
    }

    @Test
    void missingPublicIdReturnsEmpty() {
        StepVerifier.create(repository.findByPublicId(UUID.randomUUID()))
                .verifyComplete();
    }

    private reactor.core.publisher.Mono<Void> insertSeller(UUID publicId) {
        return databaseClient.sql("""
                        INSERT INTO app_users (public_id, email, role, status)
                        VALUES (:publicId, :email, 'SELLER', 'ACTIVE')
                        """)
                .bind("publicId", publicId)
                .bind("email", "seller-" + publicId + "@example.test")
                .fetch().rowsUpdated().then();
    }

    private record ListingEvidence(java.util.List<PartListingRecord> listings, long count) {
    }
}
