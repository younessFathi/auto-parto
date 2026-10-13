package auto.parto.config;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostgresqlDevelopmentFixturesIT {

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

    private String fixtureSql;
    private String reconciliationSql;

    @BeforeAll
    void migrateWithRestrictedMigrationRole() throws Exception {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), MIGRATION_USER, MIGRATION_PASSWORD)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        fixtureSql = Files.readString(Path.of("deploy/postgres/fixtures/identity-fixtures.sql"));
        reconciliationSql = Files.readString(Path.of("deploy/postgres/operations/reconcile-development-fixtures.sql"));
    }

    @Test
    void runtimeRoleCanApplyIdentityFixturesTwiceWithoutDuplicates() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), RUNTIME_USER, RUNTIME_PASSWORD)) {
            executeFixture(connection);
            executeFixture(connection);
            executeReconciliation(connection);

            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery("""
                         SELECT
                             (SELECT count(*) FROM app_users WHERE key_version = 'dev-fixture-v1') AS users,
                             (SELECT count(*) FROM buyer_profiles
                                 WHERE public_id = '00000000-0000-4000-8000-000000000201') AS buyers,
                             (SELECT count(*) FROM seller_profiles
                                WHERE public_id = '00000000-0000-4000-8000-000000000202') AS sellers,
                             (SELECT count(*) FROM part_requests
                                WHERE public_id = '00000000-0000-4000-8000-000000000301') AS part_requests,
                             (SELECT count(*) FROM part_listings
                                WHERE public_id = '00000000-0000-4000-8000-000000000302') AS part_listings,
                             (SELECT count(*) FROM offers
                                WHERE public_id = '00000000-0000-4000-8000-000000000303') AS offers,
                             (SELECT count(*) FROM wallets
                                WHERE public_id = '00000000-0000-4000-8000-000000000304') AS wallets,
                             (SELECT count(*) FROM wallet_ledger
                                WHERE public_id = '00000000-0000-4000-8000-000000000305') AS wallet_ledger,
                             (SELECT count(*) FROM device_tokens
                                WHERE public_id = '00000000-0000-4000-8000-000000000306') AS device_tokens,
                             (SELECT count(*) FROM notification_preferences
                                WHERE public_id = '00000000-0000-4000-8000-000000000307') AS notification_preferences,
                             (SELECT count(*) FROM files) AS files,
                             (SELECT count(*) FROM audit_records
                                 WHERE entity_type = 'app_users'
                                   AND entity_id IN (
                                       SELECT id FROM app_users WHERE key_version = 'dev-fixture-v1'
                                   )) AS user_audits
                         """)) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getLong("users")).isEqualTo(2);
                assertThat(resultSet.getLong("buyers")).isEqualTo(1);
                assertThat(resultSet.getLong("sellers")).isEqualTo(1);
                assertThat(resultSet.getLong("part_requests")).isEqualTo(1);
                assertThat(resultSet.getLong("part_listings")).isEqualTo(1);
                assertThat(resultSet.getLong("offers")).isEqualTo(1);
                assertThat(resultSet.getLong("wallets")).isEqualTo(1);
                assertThat(resultSet.getLong("wallet_ledger")).isEqualTo(1);
                assertThat(resultSet.getLong("device_tokens")).isEqualTo(1);
                assertThat(resultSet.getLong("notification_preferences")).isEqualTo(1);
                assertThat(resultSet.getLong("files")).isZero();
                assertThat(resultSet.getLong("user_audits")).isEqualTo(2);
            }
        }
    }

    private void executeFixture(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute(fixtureSql);
        }
    }

    private void executeReconciliation(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute(reconciliationSql);
        }
    }
}
