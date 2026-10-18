package auto.parto.audit;

import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.r2dbc.core.DatabaseClient;
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
class AuditPublisherPostgresqlIT {

    @Container
    private static final PostgreSQLContainer<?> APPLICATION_POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    private static final PostgreSQLContainer<?> AUDIT_POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private DatabaseClient applicationDatabase;
    private DatabaseClient auditDatabase;
    private R2dbcAuditOutboxRepository outbox;
    private R2dbcAuditStore auditStore;
    private AuditPublisher publisher;

    @BeforeAll
    void setUp() {
        migrate(APPLICATION_POSTGRES, "classpath:db/migration");
        migrate(AUDIT_POSTGRES, "classpath:db/audit-migration");
        applicationDatabase = DatabaseClient.create(connectionFactory(APPLICATION_POSTGRES));
        auditDatabase = DatabaseClient.create(connectionFactory(AUDIT_POSTGRES));
        outbox = new R2dbcAuditOutboxRepository(applicationDatabase);
        auditStore = new R2dbcAuditStore(auditDatabase);
        publisher = new AuditPublisher(outbox, auditStore,
                Clock.systemUTC(),
                3, 1, Duration.ofSeconds(1), Duration.ofMinutes(1),
                (requester, eventId) -> reactor.core.publisher.Mono.just("operator".equals(requester)));
    }

    @Test
    void publishesFromApplicationOutboxIntoSeparateAuditDatabaseAndIsDuplicateSafe() {
        UUID eventId = insertApplicationEvent(Instant.parse("2026-10-18T09:59:00Z"));

        AuditPublishReport report = publisher.publishBatch("worker-1", 10, Duration.ofMinutes(1)).block();

        assertThat(report).isEqualTo(new AuditPublishReport(1, 0, 0));
        assertThat(auditStore.contains(eventId).block()).isTrue();
        assertThat(statusFor(eventId)).isEqualTo("DISPATCHED");

        AuditEvent duplicate = event(eventId);
        assertThat(auditStore.append(duplicate).block()).isEqualTo(AuditAppendResult.DUPLICATE);
        assertThat(countAuditEvents(eventId)).isEqualTo(1);
    }

    @Test
    void replayRequiresOperatorAuthorizationAndReusesStableEventId() {
        UUID eventId = insertApplicationEvent(Instant.parse("2026-10-18T09:58:00Z"));
        publisher.publishBatch("worker-2", 10, Duration.ofMinutes(1)).block();

        org.junit.jupiter.api.Assertions.assertThrows(SecurityException.class,
                () -> publisher.replay(eventId, "guest").block());
        publisher.replay(eventId, "operator").block();

        assertThat(countAuditEvents(eventId)).isEqualTo(1);
        assertThat(statusFor(eventId)).isEqualTo("DISPATCHED");
    }

    private UUID insertApplicationEvent(Instant availableAt) {
        UUID eventId = UUID.randomUUID();
        applicationDatabase.sql("""
                        INSERT INTO outbox_events (event_id, event_type, aggregate_type, payload, available_at)
                        VALUES (:eventId, 'AP0013_TEST', 'TEST', '{}'::jsonb, :availableAt)
                        """)
                .bind("eventId", eventId)
                .bind("availableAt", availableAt)
                .fetch().rowsUpdated().block();
        return eventId;
    }

    private AuditEvent event(UUID eventId) {
        return new AuditEvent(eventId, "AP0013_TEST", 1,
                Instant.parse("2026-10-18T09:58:00Z"), null, "TEST", null, null,
                "application-postgresql-outbox", "{}");
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

    private long countAuditEvents(UUID eventId) {
        return auditDatabase.sql("SELECT count(*) AS total FROM audit_events WHERE event_id = :eventId")
                .bind("eventId", eventId)
                .map((row, metadata) -> row.get("total", Long.class))
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
