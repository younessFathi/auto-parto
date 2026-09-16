package auto.parto.config;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.PreparedStatement;
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
    void auditMigrationIsIdempotentAndEventsAreAppendOnly() throws SQLException {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/audit-migration")
                .load();

        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(flyway.migrate().migrationsExecuted).isZero();

        try (Connection connection = POSTGRES.createConnection("")) {
            UUID eventId = UUID.randomUUID();
            insertEvent(connection, eventId);

            assertThatThrownBy(() -> execute(connection,
                    "UPDATE audit_events SET event_type = 'CHANGED' WHERE event_id = '" + eventId + "'"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> execute(connection,
                    "DELETE FROM audit_events WHERE event_id = '" + eventId + "'"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertEvent(connection, eventId))
                    .isInstanceOf(SQLException.class);
        }
    }

    private static void insertEvent(Connection connection, UUID eventId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO audit_events "
                        + "(event_id, event_type, schema_version, occurred_at, source, payload) "
                        + "VALUES (?, 'USER_STATUS_CHANGED', 1, CURRENT_TIMESTAMP, 'test', '{}'::jsonb)")) {
            statement.setObject(1, eventId);
            statement.executeUpdate();
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
