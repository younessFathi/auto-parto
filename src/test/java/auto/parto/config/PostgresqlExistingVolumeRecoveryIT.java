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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostgresqlExistingVolumeRecoveryIT {

    private static final String BOOTSTRAP_USER = "bootstrap";
    private static final String BOOTSTRAP_PASSWORD = "bootstrap-test-password";
    private static final String RUNTIME_USER = "application_runtime";
    private static final String RUNTIME_PASSWORD = "runtime-test-password";
    private static final String MIGRATION_USER = "schema_migrator";
    private static final String MIGRATION_PASSWORD = "migration-test-password";
    private static final String LEGACY_USER = "legacy_owner";
    private static final String LEGACY_PASSWORD = "legacy-test-password";

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
                    "/docker-entrypoint-initdb.d/01-create-application-roles.sql")
            .withCopyFileToContainer(
                    MountableFile.forHostPath(Path.of(
                            "deploy/postgres/operations/provision-existing-volume.sh").toAbsolutePath()),
                    "/tmp/provision-existing-volume.sh")
            .withCopyFileToContainer(
                    MountableFile.forHostPath(Path.of(
                            "deploy/postgres/operations/provision-existing-volume.sql").toAbsolutePath()),
                    "/tmp/provision-existing-volume.sql")
            .withCopyFileToContainer(
                    MountableFile.forHostPath(Path.of(
                            "deploy/postgres/operations/verify-existing-volume.sql").toAbsolutePath()),
                    "/tmp/verify-existing-volume.sql")
            .withCopyFileToContainer(
                    MountableFile.forHostPath(Path.of(
                            "deploy/postgres/operations/verify-migration-role.sql").toAbsolutePath()),
                    "/tmp/verify-migration-role.sql");

    @BeforeAll
    void migrateAndCreateLegacyOwnedObject() throws SQLException {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), MIGRATION_USER, MIGRATION_PASSWORD)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection bootstrap = connection(BOOTSTRAP_USER, BOOTSTRAP_PASSWORD);
             Statement statement = bootstrap.createStatement()) {
            statement.execute("CREATE ROLE " + LEGACY_USER
                    + " LOGIN PASSWORD '" + LEGACY_PASSWORD + "' NOSUPERUSER NOCREATEDB NOCREATEROLE");
            statement.execute("GRANT CONNECT ON DATABASE auto_parto TO " + LEGACY_USER);
            statement.execute("GRANT USAGE, CREATE ON SCHEMA public TO " + LEGACY_USER);
        }

        try (Connection legacy = connection(LEGACY_USER, LEGACY_PASSWORD);
             Statement statement = legacy.createStatement()) {
            statement.execute("CREATE TABLE legacy_owned_table (id BIGSERIAL PRIMARY KEY, value TEXT NOT NULL)");
        }
    }

    @Test
    void recoveryReportsLegacyOwnershipRepairsThenSucceedsAfterApprovedPerObjectRepair() throws Exception {
        org.testcontainers.containers.Container.ExecResult blocked = runRecovery();

        assertThat(blocked.getExitCode()).isNotZero();
        assertThat(blocked.getStderr() + blocked.getStdout())
                .contains("Existing public tables/sequences are not owned by the migration role")
                .contains("ALTER TABLE public.legacy_owned_table OWNER TO schema_migrator;")
                .contains("ALTER SEQUENCE public.legacy_owned_table_id_seq OWNER TO schema_migrator;")
                .contains("No global ownership reassignment was performed");

        try (Connection runtime = connection(RUNTIME_USER, RUNTIME_PASSWORD);
             Statement statement = runtime.createStatement()) {
            assertThat(statement.executeUpdate(
                    "INSERT INTO public.legacy_owned_table (value) VALUES ('repaired grant')")).isEqualTo(1);
        }

        try (Connection bootstrap = connection(BOOTSTRAP_USER, BOOTSTRAP_PASSWORD);
             Statement statement = bootstrap.createStatement()) {
            statement.execute("REVOKE SELECT, INSERT, UPDATE, DELETE ON TABLE public.legacy_owned_table FROM "
                    + RUNTIME_USER);
            statement.execute("REVOKE USAGE, SELECT ON SEQUENCE public.legacy_owned_table_id_seq FROM "
                    + RUNTIME_USER);
        }

        org.testcontainers.containers.Container.ExecResult invalidRuntimeVerification =
                runRuntimeVerification();
        assertThat(invalidRuntimeVerification.getExitCode()).isNotZero();
        assertThat(invalidRuntimeVerification.getStderr() + invalidRuntimeVerification.getStdout())
                .contains("Runtime role application_runtime lacks required DML privileges")
                .contains("public.legacy_owned_table");

        try (Connection bootstrap = connection(BOOTSTRAP_USER, BOOTSTRAP_PASSWORD);
             Statement statement = bootstrap.createStatement()) {
            statement.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.legacy_owned_table TO "
                    + RUNTIME_USER);
            statement.execute("GRANT USAGE, SELECT ON SEQUENCE public.legacy_owned_table_id_seq TO "
                    + RUNTIME_USER);
            statement.execute("ALTER TABLE public.legacy_owned_table OWNER TO " + MIGRATION_USER);
            statement.execute("ALTER SEQUENCE public.legacy_owned_table_id_seq OWNER TO " + MIGRATION_USER);
        }

        org.testcontainers.containers.Container.ExecResult recovered = runRecovery();
        assertThat(recovered.getExitCode())
                .withFailMessage(recovered.getStdout() + recovered.getStderr())
                .isZero();
        assertThat(recovered.getStdout()).contains("Role provisioning and verification completed");

        try (Connection migration = connection(MIGRATION_USER, MIGRATION_PASSWORD);
             Statement statement = migration.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT has_table_privilege(current_user, 'public.flyway_schema_history', 'UPDATE')")) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getBoolean(1)).isTrue();
        }
    }

    private org.testcontainers.containers.Container.ExecResult runRecovery() throws Exception {
        return POSTGRES.execInContainer("sh", "-c",
                "export PGHOST=localhost PGPORT=5432 PGDATABASE=auto_parto "
                        + "PGUSER=" + BOOTSTRAP_USER + " PGPASSWORD=" + BOOTSTRAP_PASSWORD + " "
                        + "APP_POSTGRES_RUNTIME_USER=" + RUNTIME_USER + " "
                        + "APP_POSTGRES_RUNTIME_PASSWORD=" + RUNTIME_PASSWORD + " "
                        + "APP_POSTGRES_MIGRATION_USER=" + MIGRATION_USER + " "
                        + "APP_POSTGRES_MIGRATION_PASSWORD=" + MIGRATION_PASSWORD + "; "
                        + "sh /tmp/provision-existing-volume.sh");
    }

    private org.testcontainers.containers.Container.ExecResult runRuntimeVerification() throws Exception {
        return POSTGRES.execInContainer("sh", "-c",
                "export PGHOST=localhost PGPORT=5432 PGDATABASE=auto_parto "
                        + "PGUSER=" + BOOTSTRAP_USER + " PGPASSWORD=" + BOOTSTRAP_PASSWORD + " "
                        + "APP_POSTGRES_RUNTIME_USER=" + RUNTIME_USER + " "
                        + "APP_POSTGRES_MIGRATION_USER=" + MIGRATION_USER + "; "
                        + "psql --set=ON_ERROR_STOP=1 -f /tmp/verify-existing-volume.sql");
    }

    private static Connection connection(String user, String password) throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), user, password);
    }
}
