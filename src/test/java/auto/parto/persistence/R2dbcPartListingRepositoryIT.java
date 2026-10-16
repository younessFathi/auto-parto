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
import reactor.core.publisher.Flux;
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
                    assertThat(listing.businessStatus()).isEqualTo("DRAFT");
                    assertThat(listing.deliveryStatus()).isEqualTo("NOT_READY");
                    assertThat(listing.storageStatus()).isEqualTo("NOT_STORED");
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
                .assertNext(listing -> {
                    assertThat(listing.status()).isEqualTo("PUBLISHED");
                    assertThat(listing.businessStatus()).isEqualTo("PUBLISHED");
                    assertThat(listing.version()).isEqualTo(1);
                })
                .verifyComplete();
    }

    @Test
    void missingPublicIdReturnsEmpty() {
        StepVerifier.create(repository.findByPublicId(UUID.randomUUID()))
                .verifyComplete();
    }

    @Test
    void matchingUsesPublishedListingsAndCapsResults() {
        UUID buyer = UUID.randomUUID();
        UUID seller = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        PartRequestRecord request = new PartRequestRecord(requestId, buyer, "Brake pads", "Front",
                Condition.NEW, Category.CAR, "OPEN", 0, Instant.now(), Instant.now(),
                "Toyota", "Corolla", 2020, 0);

        StepVerifier.create(insertBuyer(buyer)
                        .then(insertSeller(seller))
                        .then(databaseClient.sql("""
                                INSERT INTO part_requests (public_id, buyer_user_id, name, condition, category,
                                                           vehicle_make, vehicle_model, vehicle_year)
                                SELECT :request, id, 'Brake pads', 'NEW', 'CAR', 'Toyota', 'Corolla', 2020
                                FROM app_users WHERE public_id = :buyer
                                """).bind("request", requestId).bind("buyer", buyer).fetch().rowsUpdated().then())
                        .then(repository.create(seller, "Matching listing", null, Condition.NEW.name(),
                                Category.CAR.name(), "Toyota", "Corolla", 2020, 1000, "MAD"))
                        .flatMap(listing -> repository.updateStatus(listing.publicId(), seller, "PUBLISHED", 0, Instant.now()))
                        .thenMany(repository.findMatches(request, 1))
                        .collectList())
                .assertNext(matches -> assertThat(matches).hasSize(1)
                        .allSatisfy(match -> assertThat(match.status()).isEqualTo("PUBLISHED")))
                .verifyComplete();
    }

    @Test
    void matchingUsesIndexBackedPlansForEverySupportedFilterShape() {
        UUID seller = UUID.randomUUID();

        StepVerifier.create(insertSeller(seller)
                        .then(seedMatchingListings(seller))
                        .then(analyzeListings())
                        .thenMany(Flux.just(
                                new MatchingPlanCase(matchingRequest(null, null, null, null, null),
                                        "part_listings_matching_order_idx"),
                                new MatchingPlanCase(matchingRequest(Condition.NEW, null, null, null, null),
                                        "part_listings_matching_condition_idx"),
                                new MatchingPlanCase(matchingRequest(null, null, "Toyota", "Corolla", 2020),
                                        "part_listings_matching_vehicle_idx"),
                                new MatchingPlanCase(matchingRequest(Condition.NEW, Category.CAR, null, null, null),
                                        "part_listings_matching_category_condition_idx"),
                                new MatchingPlanCase(matchingRequest(Condition.NEW, Category.CAR,
                                        "Toyota", "Corolla", 2020), "part_listings_matching_full_idx"))
                                .concatMap(testCase -> explainMatching(testCase.request(), testCase.expectedIndex())))
                        .then())
                .verifyComplete();
    }

    private reactor.core.publisher.Mono<Void> seedMatchingListings(UUID seller) {
        return databaseClient.sql("""
                        INSERT INTO part_listings (public_id, seller_user_id, name, condition, category,
                                                   vehicle_make, vehicle_model, vehicle_year, amount_minor,
                                                   currency, status, business_status)
                        SELECT gen_random_uuid(), au.id, 'Indexed listing ' || series,
                               CASE WHEN series % 10 = 0 THEN 'NEW' ELSE 'USED' END,
                               CASE WHEN series % 10 = 0 THEN 'CAR' ELSE 'TRUCK' END,
                               CASE WHEN series % 100 = 0 THEN 'Toyota' ELSE 'Honda' END,
                               CASE WHEN series % 100 = 0 THEN 'Corolla' ELSE 'Civic' END,
                               CASE WHEN series % 100 = 0 THEN 2020 ELSE 2021 END,
                               1000, 'MAD', 'PUBLISHED', 'PUBLISHED'
                        FROM app_users au CROSS JOIN generate_series(1, 5000) AS series
                        WHERE au.public_id = :seller
                        """)
                .bind("seller", seller).fetch().rowsUpdated().then();
    }

    private reactor.core.publisher.Mono<Void> analyzeListings() {
        return databaseClient.sql("ANALYZE part_listings").fetch().rowsUpdated().then();
    }

    private reactor.core.publisher.Mono<java.util.List<String>> explainMatching(PartRequestRecord request,
                                                                                   String expectedIndex) {
        DatabaseClient.GenericExecuteSpec query = databaseClient.sql(
                "EXPLAIN (COSTS OFF) " + R2dbcPartListingRepository.matchingSql(request))
                .bind("limit", 1);
        if (request.category() != null) query = query.bind("category", request.category().name());
        if (request.condition() != null) query = query.bind("condition", request.condition().name());
        if (request.vehicleMake() != null) query = query.bind("vehicleMake", request.vehicleMake());
        if (request.vehicleModel() != null) query = query.bind("vehicleModel", request.vehicleModel());
        if (request.vehicleYear() != null) query = query.bind("vehicleYear", request.vehicleYear());
        return query.map((row, metadata) -> row.get("QUERY PLAN", String.class)).all()
                .collectList()
                .doOnNext(plan -> assertThat(plan).anyMatch(line -> line.contains(expectedIndex)));
    }

    private PartRequestRecord matchingRequest(Condition condition, Category category,
                                              String vehicleMake, String vehicleModel, Integer vehicleYear) {
        return new PartRequestRecord(UUID.randomUUID(), UUID.randomUUID(), "Request", null,
                condition, category, "OPEN", 0, Instant.now(), Instant.now(),
                vehicleMake, vehicleModel, vehicleYear, 0);
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

    private reactor.core.publisher.Mono<Void> insertBuyer(UUID publicId) {
        return databaseClient.sql("""
                        INSERT INTO app_users (public_id, email, role, status)
                        VALUES (:publicId, :email, 'BUYER', 'ACTIVE')
                        """)
                .bind("publicId", publicId)
                .bind("email", "buyer-" + publicId + "@example.test")
                .fetch().rowsUpdated().then();
    }

    private record ListingEvidence(java.util.List<PartListingRecord> listings, long count) {
    }

    private record MatchingPlanCase(PartRequestRecord request, String expectedIndex) {
    }
}
