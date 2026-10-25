package auto.parto.audit;

import auto.parto.config.BaseIntegrationTest;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactoryOptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

import static io.r2dbc.spi.ConnectionFactoryOptions.DRIVER;
import static io.r2dbc.spi.ConnectionFactoryOptions.HOST;
import static io.r2dbc.spi.ConnectionFactoryOptions.PASSWORD;
import static io.r2dbc.spi.ConnectionFactoryOptions.PORT;
import static io.r2dbc.spi.ConnectionFactoryOptions.USER;

@AutoConfigureWebTestClient
@TestPropertySource(properties = {
        "auto.parto.audit.enabled=true",
        "auto.parto.audit.poll-interval=1h",
        "auto.parto.audit.lease-duration=2h",
        "auto.parto.audit.reconciliation-interval=1h",
        "auto.parto.audit.cleanup-interval=1h",
        "auto.parto.audit.readiness-timeout=100ms",
        "management.endpoint.health.show-details=never"
})
@Import(AuditReadinessApplicationIT.ApplicationOutboxTestConfiguration.class)
class AuditReadinessApplicationIT extends BaseIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> AUDIT_POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private WebTestClient webTestClient;

    @TestConfiguration(proxyBeanMethods = false)
    static class ApplicationOutboxTestConfiguration {

        @Bean(name = "databaseClient")
        DatabaseClient databaseClient() {
            return DatabaseClient.create(ConnectionFactories.get(ConnectionFactoryOptions.builder()
                    .option(DRIVER, "postgresql")
                    .option(HOST, "localhost")
                    .option(PORT, 1)
                    .option(USER, "unused")
                    .option(PASSWORD, "unused")
                    .build()));
        }
    }

    @DynamicPropertySource
    static void auditProperties(DynamicPropertyRegistry registry) {
        registry.add("auto.parto.audit.r2dbc-url",
                () -> AUDIT_POSTGRES.getJdbcUrl().replace("jdbc:", "r2dbc:"));
        registry.add("auto.parto.audit.username", AUDIT_POSTGRES::getUsername);
        registry.add("auto.parto.audit.password", AUDIT_POSTGRES::getPassword);
    }

    @Test
    void auditDatabaseOutageDoesNotRemoveBusinessInstanceFromReadiness() {
        assertReadiness("UP", 200);

        pauseAuditPostgres();
        try {
            assertReadiness("UP", 200);
        } finally {
            unpauseAuditPostgres();
        }
        assertReadiness("UP", 200);
    }

    private static void pauseAuditPostgres() {
        DockerClientFactory.instance().client()
                .pauseContainerCmd(AUDIT_POSTGRES.getContainerId())
                .exec();
    }

    private static void unpauseAuditPostgres() {
        DockerClientFactory.instance().client()
                .unpauseContainerCmd(AUDIT_POSTGRES.getContainerId())
                .exec();
    }

    private void assertReadiness(String expectedStatus, int expectedHttpStatus) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
        String actualBody = null;
        Integer actualHttpStatus = null;
        while (Instant.now().isBefore(deadline)) {
            EntityExchangeResult<String> result = webTestClient.get()
                    .uri("/actuator/health/readiness")
                    .exchange()
                    .expectBody(String.class)
                    .returnResult();
            actualHttpStatus = result.getStatus().value();
            actualBody = result.getResponseBody();
            if (actualHttpStatus == expectedHttpStatus) {
                assertThat(actualBody).contains("\"status\":\"" + expectedStatus + "\"");
                return;
            }
            sleepBriefly();
        }
        assertThat(actualBody).as("readiness body").contains("\"status\":\"" + expectedStatus + "\"");
        assertThat(actualHttpStatus).as("readiness HTTP status, body=%s", actualBody).isEqualTo(expectedHttpStatus);
    }

    private static void sleepBriefly() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while waiting for readiness", exception);
        }
    }
}
