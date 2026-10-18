package auto.parto.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PostgresqlMigrationResourceTest {

    @Test
    void initialMigrationContainsTargetSchemaBoundaries() throws IOException {
        String sql = readResource("/db/migration/V1__create_initial_postgresql_schema.sql");

        assertThat(sql).contains("CREATE TABLE app_users");
        assertThat(sql).contains("CREATE TABLE part_requests");
        assertThat(sql).contains("CREATE TABLE part_listings");
        assertThat(sql).contains("CREATE TABLE wallets");
        assertThat(sql).contains("CREATE TABLE wallet_ledger");
        assertThat(sql).contains("CREATE TABLE notifications");
        assertThat(sql).contains("CREATE TABLE notification_attempts");
        assertThat(sql).contains("CREATE TABLE outbox_events");
        assertThat(sql).contains("CREATE TABLE idempotency_records");
        assertThat(sql).contains("BIGSERIAL PRIMARY KEY");
        assertThat(sql).contains("public_id UUID NOT NULL UNIQUE");
        assertThat(sql).contains("provider VARCHAR(64)");
        assertThat(sql).contains("num_nonnulls(part_request_id, part_listing_id) = 1");
        assertThat(sql).contains("wallet_ledger_wallet_currency_fk");
        assertThat(sql).contains("wallet_ledger_operation_sign_chk");
        assertThat(sql).contains("outbox_events_lease_chk");
        assertThat(sql).contains("require_sensitive_update_audit");
        assertThat(sql).contains("last_audit_record_id BIGINT NOT NULL REFERENCES audit_records (id)");
        assertThat(sql).contains("create_app_user_audit");
        assertThat(sql).contains("app_users_initial_audit_trigger");
        assertThat(sql).contains("set_updated_at");
        assertThat(sql).contains("prevent_immutable_record_mutation");
        assertThat(sql).contains("lease_expires_at TIMESTAMPTZ");
    }

    @Test
    void expandContractMigrationAddsIndependentDeliveryState() throws IOException {
        String sql = readResource("/db/migration/V2__separate_audit_delivery.sql");

        assertThat(sql).contains("ADD COLUMN aggregate_public_id UUID");
        assertThat(sql).contains("CREATE TABLE outbox_deliveries");
        assertThat(sql).contains("outbox_deliveries_event_destination_uidx");
        assertThat(sql).contains("outbox_deliveries_lease_chk");
        assertThat(sql).contains("destination IN ('AUDIT', 'SSE', 'FCM', 'EMAIL')");
        assertThat(sql).contains("outbox_events_audit_delivery_trigger");
        assertThat(sql).contains("outbox_events_aggregate_public_chk");
        assertThat(sql).contains("outbox_deliveries_error_chk");
    }

    @Test
    void otpInvariantMigrationAllowsOnlyOneActiveLoginChallengePerUser() throws IOException {
        String sql = readResource("/db/migration/V3__enforce_single_active_login_otp.sql");

        assertThat(sql).contains("ROW_NUMBER() OVER");
        assertThat(sql).contains("duplicate_audits");
        assertThat(sql).contains("SET consumed_at = CURRENT_TIMESTAMP");
        assertThat(sql).contains("cannot enforce one active LOGIN OTP challenge per user");
        assertThat(sql).contains("CREATE UNIQUE INDEX otp_challenges_active_login_user_uidx");
        assertThat(sql).contains("ON otp_challenges (user_id)");
        assertThat(sql).contains("purpose = 'LOGIN'");
        assertThat(sql).contains("consumed_at IS NULL");
    }

    @Test
    void notificationPreferencesMigrationUsesCanonicalUserOwnership() throws IOException {
        String sql = readResource("/db/migration/V4__create_notification_preferences.sql");

        assertThat(sql).contains("CREATE TABLE notification_preferences");
        assertThat(sql).contains("user_id BIGINT NOT NULL UNIQUE REFERENCES app_users (id)");
        assertThat(sql).contains("email_enabled BOOLEAN NOT NULL DEFAULT TRUE");
    }

    @Test
    void matchingMigrationSupportsEveryAllowedFilterShape() throws IOException {
        String sql = readResource("/db/migration/V8__align_part_listing_matching_indexes.sql");

        assertThat(sql).contains("part_listings_matching_order_idx")
                .contains("part_listings_matching_condition_idx")
                .contains("part_listings_matching_vehicle_idx")
                .contains("part_listings_matching_category_condition_idx")
                .contains("part_listings_matching_full_idx")
                .contains("WHERE status = 'PUBLISHED'")
                .contains("LOWER(vehicle_make)")
                .contains("created_at DESC, public_id DESC");
    }

    @Test
    void walletMigrationStoresReplayStateAndReleasesExpiredLedgerKeyUniqueness() throws IOException {
        String sql = readResource("/db/migration/V9__make_wallet_idempotency_replayable.sql");

        assertThat(sql).contains("ADD COLUMN wallet_public_id UUID")
                .contains("ADD COLUMN ledger_public_id UUID")
                .contains("wallet_balance_after_minor BIGINT")
                .contains("wallet_currency VARCHAR(3)")
                .contains("wallet_version BIGINT")
                .contains("DROP INDEX IF EXISTS wallet_ledger_idempotency_uidx")
                .contains("expired rows may be purged");
    }

    @Test
    void partListingStatusMigrationSeparatesLifecycleStates() throws IOException {
        String sql = readResource("/db/migration/V6__separate_part_listing_statuses.sql");

        assertThat(sql).contains("ADD COLUMN business_status VARCHAR(32)");
        assertThat(sql).contains("ADD COLUMN delivery_status VARCHAR(32)");
        assertThat(sql).contains("ADD COLUMN storage_status VARCHAR(32)");
        assertThat(sql).contains("part_listings_business_status_chk");
        assertThat(sql).contains("part_listings_delivery_status_chk");
        assertThat(sql).contains("part_listings_storage_status_chk");
        assertThat(sql).contains("part_listings_seller_business_created_idx");
    }

    @Test
    void auditDatabaseMigrationIsAppendOnlyAndIdempotentByEventId() throws IOException {
        String sql = readResource("/db/audit-migration/V1__create_audit_event_schema.sql");

        assertThat(sql).contains("CREATE TABLE audit_events");
        assertThat(sql).contains("event_id UUID NOT NULL UNIQUE");
        assertThat(sql).contains("audit_events_immutable_trigger");
        assertThat(sql).contains("audit_events_payload_object_chk");
        assertThat(sql).contains("CREATE ROLE audit_writer NOLOGIN");
        assertThat(sql).contains("CREATE ROLE audit_reader NOLOGIN");
        assertThat(sql).contains("REVOKE UPDATE, DELETE, TRUNCATE ON audit_events");
        assertThat(Files.readString(Path.of("src/main/resources/db/audit-migration/V2__grant_audit_writer_reconciliation_read.sql")))
                .contains("GRANT SELECT ON audit_events TO audit_writer");
    }

    @Test
    void developmentFixturesAreTransactionalAndIdempotent() throws IOException {
        String sql = Files.readString(Path.of("deploy/postgres/fixtures/identity-fixtures.sql"));

        assertThat(sql).startsWith("BEGIN;");
        assertThat(sql).endsWith("COMMIT;\n");
        assertThat(sql).contains("INSERT INTO app_users");
        assertThat(sql).contains("INSERT INTO buyer_profiles");
        assertThat(sql).contains("INSERT INTO seller_profiles");
        assertThat(sql).contains("ON CONFLICT DO NOTHING");
        assertThat(sql).contains("RAISE EXCEPTION 'development buyer fixture conflicts");
        assertThat(sql).contains("RAISE EXCEPTION 'development seller fixture conflicts");
        assertThat(sql).doesNotContain("INSERT INTO otp_challenges");
        assertThat(sql).doesNotContain("INSERT INTO files");
        assertThat(sql).doesNotContain("INSERT INTO notifications");
    }

    @Test
    void reconciliationGateFailsOnMissingOrMismatchedFixtures() throws IOException {
        String sql = Files.readString(Path.of("deploy/postgres/operations/reconcile-development-fixtures.sql"));

        assertThat(sql).startsWith("BEGIN;");
        assertThat(sql).contains("development fixture reconciliation failed");
        assertThat(sql).contains("wallet balance does not equal ledger sum");
        assertThat(sql).contains("ROLLBACK;");
    }

    @Test
    void reconciliationShellWrapperDelegatesToTransactionalSqlScript() throws IOException {
        String script = Files.readString(Path.of("deploy/postgres/operations/reconcile-development-fixtures.sh"));

        assertThat(script).contains("psql --set ON_ERROR_STOP=1 --file");
        assertThat(script).contains("reconcile-development-fixtures.sql");
    }

    private String readResource(String resource) throws IOException {
        try (InputStream stream = getClass().getResourceAsStream(resource)) {
            assertThat(stream).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
