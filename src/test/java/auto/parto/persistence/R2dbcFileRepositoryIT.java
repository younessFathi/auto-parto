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

import java.util.UUID;
import java.util.List;

import static io.r2dbc.spi.ConnectionFactoryOptions.*;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class R2dbcFileRepositoryIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    private DatabaseClient databaseClient;
    private FileRepository repository;

    @BeforeAll
    void migrateAndCreateRepository() {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").load().migrate();
        var connectionFactory = io.r2dbc.spi.ConnectionFactories.get(ConnectionFactoryOptions.builder()
                .option(DRIVER, "postgresql").option(HOST, POSTGRES.getHost()).option(PORT, POSTGRES.getFirstMappedPort())
                .option(USER, POSTGRES.getUsername()).option(PASSWORD, POSTGRES.getPassword())
                .option(DATABASE, POSTGRES.getDatabaseName()).build());
        databaseClient = DatabaseClient.create(connectionFactory);
        repository = new R2dbcFileRepository(databaseClient,
                TransactionalOperator.create(new R2dbcTransactionManager(connectionFactory)));
    }

    @Test
    void createsAndListsFileForOwnedRequest() {
        UUID owner = UUID.randomUUID();
        UUID request = UUID.randomUUID();
        StepVerifier.create(insertRequest(owner, request)
                        .then(repository.createForRequest(owner, request, "objects/a", "photo.jpg", "image/jpeg", 12, "sha256:a")))
                .assertNext(file -> {
                    assertThat(file.ownerUserPublicId()).isEqualTo(owner);
                    assertThat(file.partRequestPublicId()).isEqualTo(request);
                    assertThat(file.status()).isEqualTo("PENDING");
                }).verifyComplete();
        StepVerifier.create(repository.findByRequest(request, 10)).expectNextCount(1).verifyComplete();
    }

    @Test
    void rejectsFileWhenTargetIsNotOwned() {
        UUID owner = UUID.randomUUID();
        UUID actualOwner = UUID.randomUUID();
        UUID request = UUID.randomUUID();
        StepVerifier.create(insertRequest(actualOwner, request)
                        .then(repository.createForRequest(owner, request, "objects/b", "photo.jpg", "image/jpeg", 12, null)))
                .verifyComplete();
    }

    @Test
    void findsCanonicalFileBatchWithBoundedQuery() {
        UUID owner = UUID.randomUUID();
        UUID request = UUID.randomUUID();
        StepVerifier.create(insertRequest(owner, request)
                        .then(repository.createForRequest(owner, request, "objects/batch", "photo.jpg",
                                "image/jpeg", 12, "sha"))
                        .flatMapMany(file -> repository.findByPublicIds(List.of(file.publicId()), 10)))
                .assertNext(file -> assertThat(file.objectKey()).isEqualTo("objects/batch"))
                .verifyComplete();
    }

    @Test
    void tombstonesOwnedFileAndIsIdempotent() {
        UUID owner = UUID.randomUUID();
        UUID request = UUID.randomUUID();
        StepVerifier.create(insertRequest(owner, request)
                        .then(repository.createForRequest(owner, request, "objects/c", "photo.jpg", "image/jpeg", 12, "sha"))
                        .flatMap(file -> repository.tombstone(file.publicId(), owner)
                                .flatMap(first -> repository.tombstone(file.publicId(), owner)
                                        .map(second -> java.util.Map.entry(first, second)))))
                .assertNext(result -> {
                    assertThat(result.getKey()).isTrue();
                    assertThat(result.getValue()).isFalse();
                })
                .verifyComplete();
    }

    @Test
    void activatesPendingFileOnceForItsOwner() {
        UUID owner = UUID.randomUUID();
        UUID request = UUID.randomUUID();
        StepVerifier.create(insertRequest(owner, request)
                        .then(repository.createForRequest(owner, request, "objects/active", "photo.jpg",
                                "image/jpeg", 12, "sha"))
                        .flatMapMany(file -> repository.activate(file.publicId(), owner)
                                .concatWith(repository.activate(file.publicId(), owner))))
                .assertNext(active -> assertThat(active.status()).isEqualTo("ACTIVE"))
                .verifyComplete();
    }

    @Test
    void listsOnlyActiveFilesWithAStableBound() {
        UUID owner = UUID.randomUUID();
        UUID request = UUID.randomUUID();
        StepVerifier.create(insertRequest(owner, request)
                        .then(repository.createForRequest(owner, request, "objects/reconcile", "photo.jpg",
                                "image/jpeg", 12, "sha"))
                        .flatMap(file -> repository.activate(file.publicId(), owner))
                        .flatMapMany(ignored -> repository.findActive(100)
                                .filter(file -> file.objectKey().equals("objects/reconcile"))))
                .assertNext(file -> {
                    assertThat(file.status()).isEqualTo("ACTIVE");
                    assertThat(file.objectKey()).isEqualTo("objects/reconcile");
                }).verifyComplete();
    }

    private reactor.core.publisher.Mono<Void> insertRequest(UUID owner, UUID request) {
        return databaseClient.sql("INSERT INTO app_users (public_id, email, role, status) VALUES (:owner, :email, 'BUYER', 'ACTIVE')")
                .bind("owner", owner).bind("email", "file-" + owner + "@example.test").fetch().rowsUpdated().then()
                .then(databaseClient.sql("""
                        INSERT INTO part_requests (public_id, buyer_user_id, name, condition, category, status)
                        VALUES (:request, (SELECT id FROM app_users WHERE public_id = :owner), 'Request', 'NEW', 'CAR', 'OPEN')
                        """).bind("request", request).bind("owner", owner).fetch().rowsUpdated().then());
    }
}
