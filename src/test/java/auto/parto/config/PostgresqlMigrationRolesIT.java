package auto.parto.config;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostgresqlMigrationRolesIT {

    private static final String BOOTSTRAP_USER = "bootstrap";
    private static final String BOOTSTRAP_PASSWORD = "bootstrap-test-password";
    private static final String RUNTIME_USER = "application_runtime";
    private static final String RUNTIME_PASSWORD = "runtime-test-password";
    private static final String MIGRATION_USER = "schema_migrator";
    private static final String MIGRATION_PASSWORD = "migration-test-password";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("auto_parto")
            .withUsername(BOOTSTRAP_USER)
            .withPassword(BOOTSTRAP_PASSWORD)
            .withEnv("APP_POSTGRES_RUNTIME_USER", RUNTIME_USER)
            .withEnv("APP_POSTGRES_RUNTIME_PASSWORD", RUNTIME_PASSWORD)
            .withEnv("APP_POSTGRES_MIGRATION_USER", MIGRATION_USER)
            .withEnv("APP_POSTGRES_MIGRATION_PASSWORD", MIGRATION_PASSWORD)
            .withCopyFileToContainer(
                    MountableFile.forHostPath(Path.of(
                            "deploy/postgres/init/01-create-application-roles.sql").toAbsolutePath()),
                    "/docker-entrypoint-initdb.d/01-create-application-roles.sql");

    @BeforeAll
    void migrateWithRestrictedMigrationRole() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), MIGRATION_USER, MIGRATION_PASSWORD)
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    @Test
    void runtimeRoleCanUseMigratedSchemaButCannotCreateSchemaObjects() throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), RUNTIME_USER, RUNTIME_PASSWORD)) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO app_users (public_id, email, role) VALUES (?, ?, 'BUYER')")) {
                statement.setObject(1, UUID.randomUUID());
                statement.setString(2, "runtime-fixture@example.test");
                assertThat(statement.executeUpdate()).isEqualTo(1);
            }

            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery(
                         "SELECT rolsuper, rolcreatedb, rolcreaterole FROM pg_roles WHERE rolname = current_user")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getBoolean("rolsuper")).isFalse();
                assertThat(resultSet.getBoolean("rolcreatedb")).isFalse();
                assertThat(resultSet.getBoolean("rolcreaterole")).isFalse();
            }

            assertThatThrownBy(() -> {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("CREATE TABLE runtime_must_not_create (id BIGINT)");
                }
            }).isInstanceOf(SQLException.class)
                    .hasMessageContaining("permission denied");
        }
    }
}
