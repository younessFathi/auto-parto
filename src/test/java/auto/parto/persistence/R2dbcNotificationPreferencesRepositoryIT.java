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

import static io.r2dbc.spi.ConnectionFactoryOptions.*;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class R2dbcNotificationPreferencesRepositoryIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    private DatabaseClient databaseClient;
    private NotificationPreferencesRepository repository;

    @BeforeAll
    void migrateAndCreateRepository() {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").load().migrate();
        var connectionFactory = io.r2dbc.spi.ConnectionFactories.get(ConnectionFactoryOptions.builder()
                .option(DRIVER, "postgresql").option(HOST, POSTGRES.getHost()).option(PORT, POSTGRES.getFirstMappedPort())
                .option(USER, POSTGRES.getUsername()).option(PASSWORD, POSTGRES.getPassword())
                .option(DATABASE, POSTGRES.getDatabaseName()).build());
        databaseClient = DatabaseClient.create(connectionFactory);
        repository = new R2dbcNotificationPreferencesRepository(databaseClient,
                TransactionalOperator.create(new R2dbcTransactionManager(connectionFactory)));
    }

    @Test
    void savesAndReplacesPreferencesForCanonicalOwner() {
        UUID owner = UUID.randomUUID();
        NotificationPreferencesRecord first = preferences(null, true);
        NotificationPreferencesRecord second = preferences(null, false);
        StepVerifier.create(insertUser(owner).then(repository.save(owner, first)).flatMap(saved -> repository.save(owner, second)))
                .assertNext(saved -> {
                    assertThat(saved.userPublicId()).isEqualTo(owner);
                    assertThat(saved.emailEnabled()).isFalse();
                    assertThat(saved.publicId()).isNotNull();
                }).verifyComplete();
    }

    @Test
    void missingOwnerReturnsEmpty() {
        StepVerifier.create(repository.save(UUID.randomUUID(), preferences(null, true))).verifyComplete();
    }

    private NotificationPreferencesRecord preferences(UUID publicId, boolean email) {
        return new NotificationPreferencesRecord(publicId, null, email, true, false, true, true, true, true, false, null, null);
    }

    private reactor.core.publisher.Mono<Void> insertUser(UUID publicId) {
        return databaseClient.sql("INSERT INTO app_users (public_id, email, role, status) VALUES (:id, :email, 'BUYER', 'ACTIVE')")
                .bind("id", publicId).bind("email", "pref-" + publicId + "@example.test")
                .fetch().rowsUpdated().then();
    }
}
