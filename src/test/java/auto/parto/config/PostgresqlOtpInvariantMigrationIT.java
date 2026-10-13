package auto.parto.config;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class PostgresqlOtpInvariantMigrationIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void v3RemediatesDuplicateActiveLoginChallengesBeforeCreatingIndex() throws SQLException {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .target("2")
                .load()
                .migrate();

        long userId;
        long olderChallengeId;
        long newerChallengeId;
        try (Connection connection = POSTGRES.createConnection("")) {
            userId = insertSeller(connection);
            olderChallengeId = insertChallenge(connection, userId, Instant.parse("2026-01-01T00:00:00Z"));
            newerChallengeId = insertChallenge(connection, userId, Instant.parse("2026-01-02T00:00:00Z"));
            assertThat(activeChallengeCount(connection, userId)).isEqualTo(2);
        }

        assertThat(Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate()
                .migrationsExecuted).isEqualTo(2);

        try (Connection connection = POSTGRES.createConnection("")) {
            assertThat(activeChallengeCount(connection, userId)).isEqualTo(1);
            assertThat(consumedAt(connection, olderChallengeId)).isNotNull();
            assertThat(consumedAt(connection, newerChallengeId)).isNull();
            assertThat(lastAuditMatchesChallenge(connection, olderChallengeId)).isTrue();
            assertThat(indexExists(connection)).isTrue();
        }
    }

    private static long insertSeller(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO app_users (public_id, email, role) VALUES (?, ?, 'SELLER') RETURNING id")) {
            statement.setObject(1, UUID.randomUUID());
            statement.setString(2, "migration-duplicate-" + UUID.randomUUID() + "@example.test");
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }

    private static long insertChallenge(Connection connection, long userId, Instant createdAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO otp_challenges "
                        + "(public_id, user_id, identity, purpose, code_hash, expires_at, created_at) "
                        + "VALUES (?, ?, ?, 'LOGIN', ?, ?, ?) RETURNING id")) {
            statement.setObject(1, UUID.randomUUID());
            statement.setLong(2, userId);
            statement.setString(3, "migration@example.test");
            statement.setString(4, "test-hash");
            statement.setTimestamp(5, java.sql.Timestamp.from(createdAt.plusSeconds(300)));
            statement.setTimestamp(6, java.sql.Timestamp.from(createdAt));
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }

    private static long activeChallengeCount(Connection connection, long userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT count(*) FROM otp_challenges WHERE user_id = ? "
                        + "AND purpose = 'LOGIN' AND consumed_at IS NULL")) {
            statement.setLong(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }

    private static java.sql.Timestamp consumedAt(Connection connection, long challengeId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT consumed_at FROM otp_challenges WHERE id = ?")) {
            statement.setLong(1, challengeId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getTimestamp(1);
            }
        }
    }

    private static boolean lastAuditMatchesChallenge(Connection connection, long challengeId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT EXISTS (SELECT 1 FROM otp_challenges oc "
                        + "JOIN audit_records ar ON ar.id = oc.last_audit_record_id "
                        + "WHERE oc.id = ? AND ar.entity_type = 'otp_challenges' AND ar.entity_id = oc.id)")) {
            statement.setLong(1, challengeId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getBoolean(1);
            }
        }
    }

    private static boolean indexExists(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT 1 FROM pg_indexes WHERE indexname = 'otp_challenges_active_login_user_uidx'")) {
            return resultSet.next();
        }
    }
}
