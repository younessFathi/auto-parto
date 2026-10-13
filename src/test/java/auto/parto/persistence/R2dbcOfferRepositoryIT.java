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
class R2dbcOfferRepositoryIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private DatabaseClient databaseClient;
    private OfferRepository repository;

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
        repository = new R2dbcOfferRepository(databaseClient,
                TransactionalOperator.create(new R2dbcTransactionManager(connectionFactory)));
    }

    @Test
    void createsAndReadsOfferAgainstOneRequestTarget() {
        UUID buyer = UUID.randomUUID();
        UUID seller = UUID.randomUUID();
        UUID request = UUID.randomUUID();

        StepVerifier.create(insertPartiesAndRequest(buyer, seller, request)
                        .then(repository.create(buyer, seller, request, null,
                                12500, "MAD", true, 500, "DELIVERY")))
                .assertNext(offer -> {
                    assertThat(offer.publicId()).isNotNull();
                    assertThat(offer.partRequestPublicId()).isEqualTo(request);
                    assertThat(offer.partListingPublicId()).isNull();
                    assertThat(offer.buyerUserPublicId()).isEqualTo(buyer);
                    assertThat(offer.sellerUserPublicId()).isEqualTo(seller);
                    assertThat(offer.amountMinor()).isEqualTo(12500);
                    assertThat(offer.deliveryAmountMinor()).isEqualTo(500);
                    assertThat(offer.status()).isEqualTo("PENDING");
                })
                .verifyComplete();
    }

    @Test
    void statusUpdateRequiresActorAndExpectedVersion() {
        UUID buyer = UUID.randomUUID();
        UUID seller = UUID.randomUUID();
        UUID listing = UUID.randomUUID();

        StepVerifier.create(insertPartiesAndListing(buyer, seller, listing)
                        .then(repository.create(buyer, seller, null, listing,
                                9000, "MAD", false, 0, null))
                        .flatMap(offer -> repository.updateStatus(offer.publicId(), seller,
                                "ACCEPTED", 0, Instant.now())))
                .assertNext(offer -> {
                    assertThat(offer.status()).isEqualTo("ACCEPTED");
                    assertThat(offer.partListingPublicId()).isEqualTo(listing);
                })
                .verifyComplete();
    }

    @Test
    void missingOfferReturnsEmpty() {
        StepVerifier.create(repository.findByPublicId(UUID.randomUUID()))
                .verifyComplete();
    }

    private reactor.core.publisher.Mono<Void> insertPartiesAndRequest(UUID buyer, UUID seller, UUID request) {
        return insertUser(buyer, "BUYER")
                .then(insertUser(seller, "SELLER"))
                .then(databaseClient.sql("""
                        INSERT INTO part_requests (public_id, buyer_user_id, name, condition, category)
                        SELECT :request, id, 'Request', 'NEW', 'CAR'
                        FROM app_users WHERE public_id = :buyer
                        """)
                        .bind("request", request).bind("buyer", buyer).fetch().rowsUpdated().then());
    }

    private reactor.core.publisher.Mono<Void> insertPartiesAndListing(UUID buyer, UUID seller, UUID listing) {
        return insertUser(buyer, "BUYER")
                .then(insertUser(seller, "SELLER"))
                .then(databaseClient.sql("""
                        INSERT INTO part_listings (public_id, seller_user_id, name, condition, category,
                                                   amount_minor, currency)
                        SELECT :listing, id, 'Listing', 'NEW', 'CAR', 9000, 'MAD'
                        FROM app_users WHERE public_id = :seller
                        """)
                        .bind("listing", listing).bind("seller", seller).fetch().rowsUpdated().then());
    }

    private reactor.core.publisher.Mono<Void> insertUser(UUID publicId, String role) {
        return databaseClient.sql("""
                        INSERT INTO app_users (public_id, email, role, status)
                        VALUES (:publicId, :email, :role, 'ACTIVE')
                        """)
                .bind("publicId", publicId)
                .bind("email", role.toLowerCase() + "-" + publicId + "@example.test")
                .bind("role", role)
                .fetch().rowsUpdated().then();
    }
}
