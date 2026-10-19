package auto.parto.audit;

import auto.parto.config.AuditReadinessHealthIndicator;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.actuate.health.Status;
import org.springframework.r2dbc.core.DatabaseClient;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Duration;
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
class AuditOutboxUnavailablePostgresqlIT {

    @Container
    private static final PostgreSQLContainer<?> APPLICATION_POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    private static final PostgreSQLContainer<?> AUDIT_POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private DatabaseClient applicationDatabase;

    @BeforeAll
    void setUp() {
        migrate(APPLICATION_POSTGRES, "classpath:db/migration");
        migrate(AUDIT_POSTGRES, "classpath:db/audit-migration");
        applicationDatabase = DatabaseClient.create(connectionFactory(APPLICATION_POSTGRES));
    }

    @Test
    void readinessStaysDownAndWorkerDoesNotClaimBeforeAuditDatabaseRecovers() {
        DatabaseClient auditDatabase = DatabaseClient.create(connectionFactory(AUDIT_POSTGRES));
        AuditPublisher publisher = new AuditPublisher(
                new R2dbcAuditOutboxRepository(applicationDatabase),
                new R2dbcAuditStore(auditDatabase),
                Clock.systemUTC(), 3, 1, Duration.ofSeconds(1), Duration.ofMinutes(1),
                (requester, eventId) -> reactor.core.publisher.Mono.just(true));
        AuditProcessingProperties properties = new AuditProcessingProperties();
        properties.setPollInterval(Duration.ofMillis(10));
        properties.setReadinessTimeout(Duration.ofMillis(50));
        properties.setReconciliationInterval(Duration.ofHours(1));
        properties.setCleanupInterval(Duration.ofHours(1));
        AuditReadinessHealthIndicator health = new AuditReadinessHealthIndicator(publisher, properties);
        AuditOutboxWorker worker = new AuditOutboxWorker(publisher, properties, Clock.systemUTC());
        UUID eventId = insertApplicationEvent();

        pauseAuditPostgres();
        try {
            awaitHealthStatus(health, Status.DOWN);

            worker.start();
            Thread.sleep(200);

            assertThat(statusFor(eventId)).isEqualTo("PENDING");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("test interrupted", exception);
        } finally {
            worker.stop();
            unpauseAuditPostgres();
        }
    }

    private static void awaitHealthStatus(AuditReadinessHealthIndicator health, Status expectedStatus) {
        Instant deadline = Instant.now().plusSeconds(5);
        Status actualStatus = null;
        while (Instant.now().isBefore(deadline)) {
            actualStatus = health.health().block().getStatus();
            if (expectedStatus.equals(actualStatus)) {
                return;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("interrupted while waiting for audit readiness", exception);
            }
        }
        assertThat(actualStatus).isEqualTo(expectedStatus);
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

    private UUID insertApplicationEvent() {
        UUID eventId = UUID.randomUUID();
        applicationDatabase.sql("""
                        INSERT INTO outbox_events (event_id, event_type, aggregate_type, payload, available_at)
                        VALUES (:eventId, 'AP0013_UNAVAILABLE', 'TEST', '{}'::jsonb, CURRENT_TIMESTAMP)
                        """)
                .bind("eventId", eventId)
                .fetch().rowsUpdated().block();
        return eventId;
    }

    private String statusFor(UUID eventId) {
        return applicationDatabase.sql("""
                        SELECT od.status FROM outbox_deliveries od
                        JOIN outbox_events oe ON oe.id = od.outbox_event_id
                        WHERE oe.event_id = :eventId AND od.destination = 'AUDIT'
                        """)
                .bind("eventId", eventId)
                .map((row, metadata) -> row.get("status", String.class))
                .one().block();
    }

    private static void migrate(PostgreSQLContainer<?> postgres, String location) {
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations(location).load().migrate();
    }

    private static ConnectionFactory connectionFactory(PostgreSQLContainer<?> postgres) {
        return ConnectionFactories.get(ConnectionFactoryOptions.builder()
                .option(DRIVER, "postgresql")
                .option(HOST, postgres.getHost())
                .option(PORT, postgres.getFirstMappedPort())
                .option(USER, postgres.getUsername())
                .option(PASSWORD, postgres.getPassword())
                .option(DATABASE, postgres.getDatabaseName())
                .build());
    }
}
