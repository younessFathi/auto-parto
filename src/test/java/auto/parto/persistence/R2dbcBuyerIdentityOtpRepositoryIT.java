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

import static io.r2dbc.spi.ConnectionFactoryOptions.*;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class R2dbcBuyerIdentityOtpRepositoryIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    private BuyerIdentityOtpRepository repository;

    @BeforeAll
    void migrateAndCreateRepository() {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").load().migrate();
        var connectionFactory = io.r2dbc.spi.ConnectionFactories.get(ConnectionFactoryOptions.builder()
                .option(DRIVER, "postgresql").option(HOST, POSTGRES.getHost()).option(PORT, POSTGRES.getFirstMappedPort())
                .option(USER, POSTGRES.getUsername()).option(PASSWORD, POSTGRES.getPassword())
                .option(DATABASE, POSTGRES.getDatabaseName()).build());
        repository = new R2dbcBuyerIdentityOtpRepository(DatabaseClient.create(connectionFactory),
                TransactionalOperator.create(new R2dbcTransactionManager(connectionFactory)), UUID.randomUUID().toString());
    }

    @Test
    void findsBuyerIdentityByPhoneNumberWithoutLoadingMongoData() {
        String email = "buyer-" + UUID.randomUUID() + "@example.test";
        String phone = "+212" + UUID.randomUUID().toString().replace("-", "").substring(0, 9);
        StepVerifier.create(repository.create(email, "Ada", "Lovelace", phone, "123456",
                                Instant.now().plusSeconds(300))
                        .then(repository.findByPhoneNumber(phone)))
                .assertNext(identity -> {
                    assertThat(identity.email()).isEqualTo(email);
                    assertThat(identity.phoneNumber()).isEqualTo(phone);
                    assertThat(identity.otpHash()).isNotEqualTo("123456");
                }).verifyComplete();
    }
}
