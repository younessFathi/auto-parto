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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    private UUID buyerId;

    @BeforeAll
    void migrateAndCreateRepository() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        var connectionFactory = io.r2dbc.spi.ConnectionFactories.get(ConnectionFactoryOptions.builder()
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
        buyerId = UUID.randomUUID();
        databaseClient.sql("""
                        INSERT INTO app_users (public_id, email, role, status)
                        VALUES (:publicId, :email, 'BUYER', 'ACTIVE')
                        """)
                .bind("publicId", buyerId)
                .bind("email", "part-request-" + buyerId + "@example.test")
                .fetch().rowsUpdated().block();
    }

    @Test
    void createsListsAndTransitionsOwnedRequest() {
        UUID testBuyerId = createBuyer("list");
        PartRequestRecord created = repository.create(testBuyerId, "Brake pads", "Front axle", "NEW", "BRAKES",
                                "Toyota", "Corolla", 2020)
                .flatMap(request -> repository.findByPublicId(request.publicId())
                        .doOnNext(found -> assertThat(found.buyerUserPublicId()).isEqualTo(testBuyerId))
                        .flatMap(found -> repository.transition(found.publicId(), testBuyerId, "CLOSED")))
                .block();
        assertThat(created.status()).isEqualTo("CLOSED");
        assertThat(created.version()).isEqualTo(1);

        List<PartRequestRecord> firstPage = repository.findByBuyerUserPublicId(testBuyerId, null, null, 1)
                .collectList().block();
        assertThat(firstPage).hasSize(1);
        assertThat(firstPage.get(0).status()).isEqualTo("CLOSED");

        StepVerifier.create(repository.findByBuyerUserPublicId(testBuyerId,
                        firstPage.get(0).createdAt(), firstPage.get(0).publicId(), 1))
                .expectNextCount(0)
                .verifyComplete();
    }

    @Test
    void keysetPaginationIsDeterministicForTiedTimestampsAndOwnsResults() {
        UUID testBuyerId = createBuyer("keyset");
        UUID otherBuyerId = UUID.randomUUID();
        databaseClient.sql("""
                        INSERT INTO app_users (public_id, email, role, status)
                        VALUES (:publicId, :email, 'BUYER', 'ACTIVE')
                        """)
                .bind("publicId", otherBuyerId)
                .bind("email", "other-part-request-" + otherBuyerId + "@example.test")
                .fetch().rowsUpdated().block();

        List<PartRequestRecord> created = java.util.stream.IntStream.range(0, 5)
                .mapToObj(index -> repository.create(testBuyerId, "Brake pads " + index, null, null,
                        null, null, null, null).block())
                .toList();
        PartRequestRecord otherBuyerRequest = repository.create(otherBuyerId, "Other buyer", null, null,
                null, null, null, null).block();
        Instant tiedCreatedAt = Instant.parse("2099-01-01T00:00:00Z");
        for (PartRequestRecord request : created) {
            setCreatedAt(request.publicId(), tiedCreatedAt);
        }
        setCreatedAt(otherBuyerRequest.publicId(), tiedCreatedAt);

        List<PartRequestRecord> firstPage = repository
                .findByBuyerUserPublicId(testBuyerId, null, null, 3).collectList().block();
        assertThat(firstPage).hasSize(3);
        assertThat(firstPage).allMatch(request -> request.buyerUserPublicId().equals(testBuyerId));
        PartRequestRecord cursor = firstPage.get(2);
        List<PartRequestRecord> secondPage = repository
                .findByBuyerUserPublicId(testBuyerId, cursor.createdAt(), cursor.publicId(), 3)
                .collectList().block();

        assertThat(secondPage).hasSize(2);
        assertThat(secondPage).allMatch(request -> request.buyerUserPublicId().equals(testBuyerId));
        Set<UUID> allIds = new HashSet<>();
        firstPage.forEach(request -> allIds.add(request.publicId()));
        secondPage.forEach(request -> allIds.add(request.publicId()));
        assertThat(allIds).containsExactlyInAnyOrderElementsOf(
                created.stream().map(PartRequestRecord::publicId).toList());
        List<PartRequestRecord> repeatedFirstPage = repository
                .findByBuyerUserPublicId(testBuyerId, null, null, 3).collectList().block();
        assertThat(repeatedFirstPage.stream().map(PartRequestRecord::publicId).toList())
                .containsExactlyElementsOf(firstPage.stream().map(PartRequestRecord::publicId).toList());
    }

    @Test
    void unknownBuyerCannotCreateRequest() {
        UUID unknownBuyerId = UUID.randomUUID();
        StepVerifier.create(repository.create(unknownBuyerId, "Brake pads", null, null, null,
                                null, null, null))
                .verifyComplete();

        StepVerifier.create(repository.findByBuyerUserPublicId(unknownBuyerId, null, null, 100))
                .expectNextCount(0)
                .verifyComplete();
    }

    private void setCreatedAt(UUID publicId, Instant createdAt) {
        databaseClient.sql("UPDATE part_requests SET created_at = :createdAt, updated_at = :createdAt "
                        + "WHERE public_id = :publicId")
                .bind("createdAt", createdAt)
                .bind("publicId", publicId)
                .fetch().rowsUpdated().block();
    }

    private UUID createBuyer(String label) {
        UUID publicId = UUID.randomUUID();
        databaseClient.sql("""
                        INSERT INTO app_users (public_id, email, role, status)
                        VALUES (:publicId, :email, 'BUYER', 'ACTIVE')
                        """)
                .bind("publicId", publicId)
                .bind("email", "part-request-" + label + "-" + publicId + "@example.test")
                .fetch().rowsUpdated().block();
        return publicId;
    }
}
