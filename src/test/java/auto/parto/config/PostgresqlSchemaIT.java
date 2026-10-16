package auto.parto.config;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostgresqlSchemaIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void migrationCreatesSchemaIndexesAndConstraintsIdempotently() throws SQLException {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load();

        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(8);
        assertThat(flyway.migrate().migrationsExecuted).isZero();

        try (Connection connection = POSTGRES.createConnection("")) {
            assertThat(tableNames(connection)).containsAll(List.of(
                    "app_users",
                    "buyer_profiles",
                    "seller_profiles",
                    "addresses",
                    "part_requests",
                    "part_listings",
                    "offers",
                    "wallets",
                    "wallet_ledger",
                    "otp_challenges",
                    "files",
                    "device_tokens",
                    "notifications",
                    "notification_attempts",
                    "outbox_events",
                    "outbox_deliveries",
                    "idempotency_records",
                    "audit_records"));

            assertThat(indexExists(connection, "outbox_events", "outbox_events_claim_idx")).isTrue();
            assertThat(indexExists(connection, "outbox_deliveries", "outbox_deliveries_claim_idx")).isTrue();
            assertThat(indexExists(connection, "part_requests", "part_requests_search_idx")).isTrue();
            assertThat(indexExists(connection, "part_requests", "part_requests_vehicle_search_idx")).isTrue();
            assertThat(indexExists(connection, "part_requests", "part_requests_buyer_created_public_idx")).isTrue();
            assertThat(indexExists(connection, "wallet_ledger", "wallet_ledger_idempotency_uidx")).isTrue();
            assertThat(indexExists(connection, "otp_challenges", "otp_challenges_active_login_user_uidx")).isTrue();
            assertThat(indexExists(connection, "part_listings", "part_listings_vehicle_search_idx")).isTrue();
            assertThat(indexExists(connection, "part_listings", "part_listings_seller_business_created_idx")).isTrue();
            assertThat(indexExists(connection, "part_listings", "part_listings_matching_order_idx")).isTrue();
            assertThat(indexExists(connection, "part_listings", "part_listings_matching_condition_idx")).isTrue();
            assertThat(indexExists(connection, "part_listings", "part_listings_matching_vehicle_idx")).isTrue();
            assertThat(indexExists(connection, "part_listings", "part_listings_matching_category_condition_idx")).isTrue();
            assertThat(indexExists(connection, "part_listings", "part_listings_matching_full_idx")).isTrue();

            assertThat(constraintExists(connection, "offers", "offers_target_chk")).isTrue();
            assertThat(constraintExists(connection, "offers", "offers_request_owner_fk")).isTrue();
            assertThat(constraintExists(connection, "files", "files_target_chk")).isTrue();
            assertThat(constraintExists(connection, "files", "files_request_owner_fk")).isTrue();
            assertThat(constraintExists(connection, "wallet_ledger", "wallet_ledger_wallet_currency_fk")).isTrue();
            assertThat(constraintExists(connection, "wallet_ledger", "wallet_ledger_operation_sign_chk")).isTrue();
            assertThat(constraintExists(connection, "outbox_events", "outbox_events_lease_chk")).isTrue();
            assertThat(constraintExists(connection, "outbox_deliveries", "outbox_deliveries_lease_chk")).isTrue();
            assertThat(constraintExists(connection, "outbox_deliveries", "outbox_deliveries_event_destination_uidx")).isTrue();
            assertThat(constraintExists(connection, "notification_attempts", "notification_attempts_status_chk")).isTrue();
            assertThat(constraintExists(connection, "device_tokens", "device_tokens_invalidated_at_chk")).isTrue();
            assertThat(constraintExists(connection, "part_listings", "part_listings_business_status_chk")).isTrue();
            assertThat(constraintExists(connection, "part_listings", "part_listings_delivery_status_chk")).isTrue();
            assertThat(constraintExists(connection, "part_listings", "part_listings_storage_status_chk")).isTrue();
            assertThat(triggerExists(connection, "wallet_ledger_immutable_trigger")).isTrue();
            assertThat(triggerExists(connection, "audit_records_immutable_trigger")).isTrue();
            assertThat(triggerExists(connection, "app_users_audit_trigger")).isTrue();
            assertThat(triggerExists(connection, "app_users_initial_audit_trigger")).isTrue();
            assertThat(triggerExists(connection, "app_users_updated_at_trigger")).isTrue();
            assertThat(triggerExists(connection, "outbox_events_audit_delivery_trigger")).isTrue();

            long buyerId = insertUser(connection, "BUYER", "buyer@example.test");
            long sellerId = insertUser(connection, "SELLER", "seller@example.test");
            assertCreationAudit(connection, "app_users", buyerId, null);
            assertCreationAudit(connection, "app_users", sellerId, null);
            long requestId = insertRequest(connection, buyerId);
            long listingId = insertListing(connection, sellerId);
            long notificationId = insertNotification(connection, buyerId);
            seedQueryPlanRows(connection, buyerId, sellerId, requestId);
            assertThat(countDeliveries(connection, "AUDIT")).isGreaterThan(0);
            execute(connection, "ANALYZE");

            assertThat(explainUsesIndex(connection,
                    "SELECT * FROM part_requests WHERE buyer_user_id = " + buyerId + " "
                            + "ORDER BY created_at DESC")).contains("part_requests_buyer_created_idx");
            assertThat(explainUsesIndex(connection,
                    "SELECT * FROM part_requests WHERE buyer_user_id = " + buyerId + " "
                            + "ORDER BY created_at DESC", false)).contains("part_requests_buyer_created_idx");
            assertThat(explainUsesIndex(connection,
                    "SELECT * FROM app_users WHERE public_id = '00000000-0000-0000-0000-000000000001'"))
                    .contains("app_users_public_id_key");
            assertThat(explainUsesIndex(connection,
                    "SELECT * FROM app_users WHERE public_id = '00000000-0000-0000-0000-000000000001'", false))
                    .contains("app_users_public_id_key");
            assertThat(explainUsesIndex(connection,
                    "SELECT * FROM outbox_events WHERE status = 'PENDING' "
                            + "AND available_at <= CURRENT_TIMESTAMP ORDER BY available_at"))
                    .contains("outbox_events_claim_idx");
            assertThat(explainUsesIndex(connection,
                    "SELECT * FROM outbox_events WHERE status = 'PENDING' "
                            + "AND available_at <= CURRENT_TIMESTAMP ORDER BY available_at", false))
                    .contains("outbox_events_claim_idx");
            assertThat(explainUsesIndex(connection,
                    "SELECT * FROM part_requests WHERE LOWER(name) = 'brake pad' "
                            + "AND category = 'BRAKES' AND status = 'OPEN'"))
                    .contains("part_requests_search_idx");
            assertThat(explainUsesIndex(connection,
                    "SELECT * FROM part_requests WHERE LOWER(name) = 'brake pad' "
                            + "AND category = 'BRAKES' AND status = 'OPEN'", false))
                    .contains("part_requests_search_idx");
            assertThat(explainUsesIndex(connection,
                    "SELECT * FROM part_listings WHERE seller_user_id = " + sellerId + " "
                            + "ORDER BY created_at DESC")).contains("part_listings_seller_created_idx");
            assertThat(explainUsesIndex(connection,
                    "SELECT * FROM part_requests WHERE LOWER(vehicle_make) = 'toyota' "
                            + "AND LOWER(vehicle_model) = 'corolla' AND vehicle_year = 2020 AND status = 'OPEN'"))
                    .contains("part_requests_vehicle_search_idx");
            assertThat(explainUsesIndex(connection,
                    "SELECT * FROM offers WHERE part_request_id = " + requestId + " ORDER BY created_at DESC"))
                    .contains("offers_request_created_idx");
            assertThat(explainUsesIndex(connection,
                    "SELECT * FROM notifications WHERE recipient_user_id = " + buyerId + " "
                            + "AND status = 'PENDING' ORDER BY created_at DESC"))
                    .contains("notifications_recipient_status_idx");
            assertThat(explainUsesIndex(connection,
                    "SELECT * FROM files WHERE owner_user_id = 1 AND status = 'ACTIVE'"))
                    .contains("files_owner_status_idx");
            assertThat(explainUsesIndex(connection,
                    "SELECT * FROM wallet_ledger WHERE wallet_id = 1 ORDER BY created_at DESC"))
                    .contains("wallet_ledger_wallet_created_idx");

            assertThatThrownBy(() -> insertInvalidUser(connection))
                    .isInstanceOf(SQLException.class);

            assertThatThrownBy(() -> execute(connection,
                    "INSERT INTO app_users (public_id, provider, role, email) "
                            + "VALUES ('00000000-0000-0000-0000-000000000017', 'google', 'BUYER', 'pair@example.test')"))
                    .isInstanceOf(SQLException.class);

            assertThatThrownBy(() -> execute(connection,
                    "INSERT INTO buyer_profiles (public_id, user_id) VALUES ('00000000-0000-0000-0000-000000000010', "
                            + sellerId + ")")).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> execute(connection,
                    "INSERT INTO part_requests (public_id, buyer_user_id, name) VALUES "
                            + "('" + publicId(connection, "part_requests", requestId) + "', " + buyerId + ", 'duplicate')"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> execute(connection,
                    "INSERT INTO part_requests (public_id, buyer_user_id, name) VALUES "
                            + "('00000000-0000-0000-0000-000000000011', " + sellerId + ", 'invalid owner')"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> execute(connection,
                    "INSERT INTO offers (public_id, part_request_id, buyer_user_id, seller_user_id, amount_minor, currency) "
                            + "VALUES ('00000000-0000-0000-0000-000000000012', " + requestId + ", " + sellerId
                            + ", " + sellerId + ", 1000, 'USD')")).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> execute(connection,
                    "INSERT INTO offers (public_id, part_request_id, part_listing_id, buyer_user_id, seller_user_id, amount_minor, currency) "
                            + "VALUES ('00000000-0000-0000-0000-000000000013', " + requestId + ", " + listingId
                            + ", " + buyerId + ", " + sellerId + ", 1000, 'USD')")).isInstanceOf(SQLException.class);

            long auditId = insertAuditRecord(connection);
            long walletId = insertWallet(connection, sellerId, "USD");
            assertThatThrownBy(() -> execute(connection,
                    "INSERT INTO wallet_ledger (public_id, wallet_id, amount_minor, balance_after_minor, currency, operation, audit_record_id) "
                            + "VALUES ('00000000-0000-0000-0000-000000000014', " + walletId + ", 1000, 1000, 'EUR', 'CREDIT', " + auditId + ")"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> execute(connection,
                    "INSERT INTO wallet_ledger (public_id, wallet_id, amount_minor, balance_after_minor, currency, operation, audit_record_id) "
                            + "VALUES ('00000000-0000-0000-0000-000000000018', " + walletId + ", -1000, 0, 'USD', 'CREDIT', " + auditId + ")"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> execute(connection,
                    "INSERT INTO outbox_events (event_id, event_type, aggregate_type, payload, status) "
                            + "VALUES ('00000000-0000-0000-0000-000000000015', 'test', 'test', '{}'::jsonb, 'CLAIMED')"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> execute(connection,
                    "INSERT INTO notification_attempts (public_id, notification_id, channel, status) "
                            + "VALUES ('00000000-0000-0000-0000-000000000016', " + notificationId + ", 'SSE', 'ACCEPTED')"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> execute(connection,
                    "UPDATE audit_records SET action = 'changed' WHERE id = " + auditId))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> execute(connection,
                    "UPDATE app_users SET email = 'changed@example.test' WHERE id = " + buyerId))
                    .isInstanceOf(SQLException.class);

            long userUpdateAuditId = insertAuditRecord(connection, "UPDATE", "app_users", buyerId);
            execute(connection, "UPDATE app_users SET email = 'changed@example.test', last_audit_record_id = "
                    + userUpdateAuditId + " WHERE id = " + buyerId);
        }
    }

    private static List<String> tableNames(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_type = 'BASE TABLE'")) {
            try (ResultSet resultSet = statement.executeQuery()) {
                List<String> names = new java.util.ArrayList<>();
                while (resultSet.next()) {
                    names.add(resultSet.getString(1));
                }
                return names;
            }
        }
    }

    private static boolean indexExists(Connection connection, String table, String index) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM pg_indexes WHERE schemaname = 'public' AND tablename = ? AND indexname = ?")) {
            statement.setString(1, table);
            statement.setString(2, index);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private static boolean constraintExists(Connection connection, String table, String constraint)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM pg_constraint c "
                        + "JOIN pg_class t ON t.oid = c.conrelid "
                        + "WHERE t.relname = ? AND c.conname = ?")) {
            statement.setString(1, table);
            statement.setString(2, constraint);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private static boolean triggerExists(Connection connection, String trigger) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM pg_trigger WHERE tgname = ? AND NOT tgisinternal")) {
            statement.setString(1, trigger);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private static String explainUsesIndex(Connection connection, String query) throws SQLException {
        return explainUsesIndex(connection, query, true);
    }

    private static String explainUsesIndex(Connection connection, String query, boolean disableSeqscan)
            throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET enable_seqscan = " + (disableSeqscan ? "off" : "on"));
            try (ResultSet resultSet = statement.executeQuery("EXPLAIN " + query)) {
                StringBuilder plan = new StringBuilder();
                while (resultSet.next()) {
                    plan.append(resultSet.getString(1)).append('\n');
                }
                return plan.toString();
            }
        }
    }

    private static void seedQueryPlanRows(Connection connection, long buyerId, long sellerId, long requestId)
            throws SQLException {
        execute(connection, "INSERT INTO app_users (public_id, email, role) "
                + "SELECT md5('plan-user-' || i)::uuid, 'plan-user-' || i || '@example.test', 'BUYER' "
                + "FROM generate_series(1, 1000) AS series(i)");
        execute(connection, "INSERT INTO part_requests "
                + "(public_id, buyer_user_id, name, category, vehicle_make, vehicle_model, vehicle_year, status) "
                + "SELECT md5('plan-request-' || i)::uuid, " + buyerId
                + ", CASE WHEN i = 1 THEN 'Brake pad' ELSE 'Other part' END, 'BRAKES', "
                + "CASE WHEN i = 1 THEN 'Toyota' ELSE 'Ford' END, "
                + "CASE WHEN i = 1 THEN 'Corolla' ELSE 'Focus' END, "
                + "CASE WHEN i = 1 THEN 2020 ELSE 2019 END, 'OPEN' "
                + "FROM generate_series(1, 1000) AS series(i)");
        execute(connection, "INSERT INTO part_listings "
                + "(public_id, seller_user_id, name, category, vehicle_make, vehicle_model, vehicle_year, amount_minor, currency, status) "
                + "SELECT md5('plan-listing-' || i)::uuid, " + sellerId
                + ", CASE WHEN i = 1 THEN 'Brake pad' ELSE 'Other part' END, 'BRAKES', 'Toyota', 'Corolla', 2020, 1000, 'USD', 'PUBLISHED' "
                + "FROM generate_series(1, 1000) AS series(i)");
        execute(connection, "INSERT INTO offers "
                + "(public_id, part_request_id, buyer_user_id, seller_user_id, amount_minor, currency) "
                + "SELECT md5('plan-offer-' || i)::uuid, " + requestId + ", " + buyerId + ", " + sellerId + ", 1000, 'USD' "
                + "FROM generate_series(1, 1000) AS series(i)");
        execute(connection, "INSERT INTO notifications "
                + "(public_id, recipient_user_id, event_type, payload) "
                + "SELECT md5('plan-notification-' || i)::uuid, " + buyerId + ", 'TEST', '{}'::jsonb "
                + "FROM generate_series(1, 1000) AS series(i)");
        execute(connection, "INSERT INTO outbox_events "
                + "(event_id, event_type, aggregate_type, aggregate_id, payload, aggregate_public_id) "
                + "SELECT md5('plan-outbox-' || i)::uuid, 'TEST', 'part_request', " + requestId + ", '{}'::jsonb, "
                + "(SELECT public_id FROM part_requests WHERE id = " + requestId + ") "
                + "FROM generate_series(1, 1000) AS series(i)");
    }

    private static String publicId(Connection connection, String table, long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT public_id FROM " + table + " WHERE id = ?")) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getString(1);
            }
        }
    }

    private static long countDeliveries(Connection connection, String destination) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT count(*) FROM outbox_deliveries WHERE destination = ?")) {
            statement.setString(1, destination);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }

    private static void insertInvalidUser(Connection connection) throws SQLException {
        execute(connection, "INSERT INTO app_users (public_id, email, role) "
                + "VALUES ('00000000-0000-0000-0000-000000000001', 'invalid@example.test', 'INVALID')");
    }

    private static long insertAuditRecord(Connection connection) throws SQLException {
        return insertAuditRecord(connection, "CREATE", "TEST", null);
    }

    private static long insertAuditRecord(Connection connection, String action, String entityType, Long entityId)
            throws SQLException {
        String entityIdSql = entityId == null ? "NULL" : entityId.toString();
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "INSERT INTO audit_records (public_id, action, entity_type, entity_id) "
                             + "VALUES ('" + UUID.randomUUID() + "', '" + action + "', '" + entityType + "', "
                             + entityIdSql + ") RETURNING id")) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private static long insertUser(Connection connection, String role, String email) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "INSERT INTO app_users (public_id, email, role) VALUES ('"
                             + UUID.randomUUID() + "', '" + email + "', '" + role + "') RETURNING id")) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private static void assertCreationAudit(Connection connection, String entityType, long entityId,
                                            Long expectedActorId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT created_audit_record_id, last_audit_record_id FROM " + entityType + " WHERE id = ?")) {
            statement.setLong(1, entityId);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                long createdAuditId = resultSet.getLong("created_audit_record_id");
                assertThat(resultSet.getLong("last_audit_record_id")).isEqualTo(createdAuditId);

                try (PreparedStatement auditStatement = connection.prepareStatement(
                        "SELECT actor_user_id, action, entity_type, entity_id FROM audit_records WHERE id = ?")) {
                    auditStatement.setLong(1, createdAuditId);
                    try (ResultSet auditResult = auditStatement.executeQuery()) {
                        assertThat(auditResult.next()).isTrue();
                        if (expectedActorId == null) {
                            assertThat(auditResult.getObject("actor_user_id")).isNull();
                        } else {
                            assertThat(auditResult.getLong("actor_user_id")).isEqualTo(expectedActorId);
                        }
                        assertThat(auditResult.getString("action")).isEqualTo("CREATE");
                        assertThat(auditResult.getString("entity_type")).isEqualTo(entityType);
                        assertThat(auditResult.getLong("entity_id")).isEqualTo(entityId);
                    }
                }
            }
        }
    }

    private static long insertRequest(Connection connection, long buyerId) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "INSERT INTO part_requests (public_id, buyer_user_id, name) VALUES ('"
                             + UUID.randomUUID() + "', " + buyerId + ", 'Brake pad') RETURNING id")) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private static long insertListing(Connection connection, long sellerId) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "INSERT INTO part_listings (public_id, seller_user_id, name, amount_minor, currency) VALUES ('"
                             + UUID.randomUUID() + "', " + sellerId + ", 'Brake pad', 1000, 'USD') RETURNING id")) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private static long insertWallet(Connection connection, long ownerId, String currency) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "INSERT INTO wallets (public_id, owner_user_id, currency) VALUES ('"
                             + UUID.randomUUID() + "', " + ownerId + ", '" + currency + "') RETURNING id")) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private static long insertNotification(Connection connection, long recipientId) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "INSERT INTO notifications (public_id, recipient_user_id, event_type, payload) VALUES ('"
                             + UUID.randomUUID() + "', " + recipientId + ", 'TEST', '{}'::jsonb) RETURNING id")) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
