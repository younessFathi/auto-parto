package auto.parto.config;

import auto.parto.audit.AuditEvent;
import auto.parto.audit.AuditStore;
import auto.parto.audit.R2dbcAuditStore;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class AuditPostgresqlSchemaIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void existingV1VolumeUpgradesWithV2AndKeepsRuntimeRoleAppendOnly() throws Exception {
        Flyway initial = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/audit-migration")
                .target("1")
                .load();

        assertThat(initial.migrate().migrationsExecuted).isEqualTo(1);

        String runtimeRole = "audit_runtime_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = POSTGRES.createConnection("")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE ROLE " + runtimeRole + " LOGIN PASSWORD 'runtime-secret'");
                statement.execute("GRANT audit_writer TO " + runtimeRole);
            }
        }

        Flyway upgrade = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/audit-migration")
                .load();
        assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(upgrade.migrate().migrationsExecuted).isZero();

        AuditStore runtimeStore = new R2dbcAuditStore(
                org.springframework.r2dbc.core.DatabaseClient.create(
                        connectionFactory(runtimeRole, "runtime-secret")));
        UUID eventId = UUID.randomUUID();
        AuditEvent event = new AuditEvent(eventId, "MIGRATION_UPGRADE", 1,
                java.time.Instant.now(), null, "TEST", null, null,
                "test", "{}");
        assertThat(runtimeStore.append(event).block()).isEqualTo(auto.parto.audit.AuditAppendResult.CREATED);
        assertThat(runtimeStore.contains(eventId).block()).isTrue();

        try (Connection connection = POSTGRES.createConnection("")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET ROLE " + runtimeRole);

                assertThatThrownBy(() -> execute(connection,
                        "UPDATE audit_events SET event_type = 'CHANGED' WHERE event_id = '" + eventId + "'"))
                        .isInstanceOf(SQLException.class);
                assertThatThrownBy(() -> execute(connection,
                        "DELETE FROM audit_events WHERE event_id = '" + eventId + "'"))
                        .isInstanceOf(SQLException.class);
            }
        }
    }

    private ConnectionFactory connectionFactory(String username, String password) {
        return ConnectionFactories.get(ConnectionFactoryOptions.builder()
                .option(ConnectionFactoryOptions.DRIVER, "postgresql")
                .option(ConnectionFactoryOptions.HOST, POSTGRES.getHost())
                .option(ConnectionFactoryOptions.PORT, POSTGRES.getFirstMappedPort())
                .option(ConnectionFactoryOptions.USER, username)
                .option(ConnectionFactoryOptions.PASSWORD, password)
                .option(ConnectionFactoryOptions.DATABASE, POSTGRES.getDatabaseName())
                .build());
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
