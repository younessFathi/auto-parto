package auto.parto.persistence;

import io.r2dbc.spi.R2dbcException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

@Repository
@ConditionalOnExpression("'${auto.parto.persistence.postgresql.buyer-identity.enabled:false}' == 'true'")
public final class R2dbcBuyerIdentityOtpRepository implements BuyerIdentityOtpRepository {

    private static final String COLUMNS = """
            au.public_id, au.email, au.status, bp.first_name, bp.last_name, bp.phone_number,
            oc.code_hash, oc.expires_at, oc.failed_attempts, oc.locked_until
            """;

    private final DatabaseClient databaseClient;
    private final TransactionalOperator transactions;
    private final byte[] otpPepper;

    public R2dbcBuyerIdentityOtpRepository(DatabaseClient databaseClient,
                                           TransactionalOperator transactions,
                                           @Value("${security.otp.pepper:}") String otpPepper) {
        if (otpPepper == null || otpPepper.isBlank()) {
            throw new IllegalStateException("security.otp.pepper must be configured when PostgreSQL buyer identity is enabled");
        }
        this.databaseClient = databaseClient;
        this.transactions = transactions;
        this.otpPepper = otpPepper.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public Mono<BuyerIdentityRecord> findByEmail(String email) {
        requireEmail(email);
        return databaseClient.sql(selectSql())
                .bind("email", email)
                .map((row, metadata) -> map(row))
                .one();
    }

    @Override
    public Mono<BuyerIdentityRecord> create(String email, String firstName, String lastName,
                                            String phoneNumber, String otp, Instant expiresAt) {
        requireEmail(email);
        requireOtp(otp, expiresAt);
        return transactions.transactional(databaseClient.sql("""
                        INSERT INTO app_users (public_id, email, role, status)
                        VALUES (gen_random_uuid(), :email, 'BUYER', 'ACTIVE') RETURNING id
                        """)
                .bind("email", email).map((row, metadata) -> row.get("id", Long.class)).one()
                .flatMap(userId -> insertProfile(userId, firstName, lastName, phoneNumber)
                        .then(insertOtp(userId, email, otp, expiresAt))))
                .then(findByEmail(email));
    }

    @Override
    public Mono<BuyerIdentityRecord> replaceOtp(String email, String otp, Instant expiresAt) {
        requireEmail(email);
        requireOtp(otp, expiresAt);
        return Mono.defer(() -> transactions.transactional(
                        invalidateActive(email, Instant.now()).then(insertReplacement(email, otp, expiresAt)))
                .then(findByEmail(email)))
                .retryWhen(Retry.max(1).filter(R2dbcBuyerIdentityOtpRepository::isUniqueViolation)
                        .onRetryExhaustedThrow((spec, signal) -> signal.failure()));
    }

    @Override
    public Mono<Boolean> consumeValidOtp(String email, String otp, Instant now) {
        requireEmail(email);
        if (otp == null || otp.isBlank()) return Mono.just(false);
        return databaseClient.sql("""
                        WITH target AS (
                            SELECT oc.id, oc.user_id FROM otp_challenges oc
                            JOIN app_users au ON au.id = oc.user_id
                            WHERE LOWER(au.email) = LOWER(:email) AND au.role = 'BUYER'
                              AND oc.purpose = 'LOGIN' AND oc.consumed_at IS NULL
                              AND oc.code_hash = :codeHash AND oc.expires_at > :now
                              AND (oc.locked_until IS NULL OR oc.locked_until <= :now)
                            ORDER BY oc.created_at DESC LIMIT 1 FOR UPDATE
                        ), audit AS (
                            INSERT INTO audit_records (public_id, actor_user_id, action, entity_type, entity_id)
                            SELECT gen_random_uuid(), user_id, 'UPDATE', 'otp_challenges', id FROM target
                            RETURNING id AS audit_id, entity_id
                        )
                        UPDATE otp_challenges oc SET consumed_at = :now, last_audit_record_id = audit.audit_id
                        FROM audit WHERE oc.id = audit.entity_id
                        """)
                .bind("email", email).bind("codeHash", hashOtp(otp)).bind("now", now)
                .fetch().rowsUpdated().map(rows -> rows == 1);
    }

    @Override
    public Mono<Void> recordFailedAttempt(String email, Instant now) {
        requireEmail(email);
        return databaseClient.sql("""
                        WITH target AS (
                            SELECT oc.id, oc.user_id FROM otp_challenges oc
                            JOIN app_users au ON au.id = oc.user_id
                            WHERE LOWER(au.email) = LOWER(:email) AND au.role = 'BUYER'
                              AND oc.purpose = 'LOGIN' AND oc.consumed_at IS NULL
                            ORDER BY oc.created_at DESC LIMIT 1 FOR UPDATE
                        ), audit AS (
                            INSERT INTO audit_records (public_id, actor_user_id, action, entity_type, entity_id)
                            SELECT gen_random_uuid(), user_id, 'UPDATE', 'otp_challenges', id FROM target
                            RETURNING id AS audit_id, entity_id
                        )
                        UPDATE otp_challenges oc
                        SET failed_attempts = oc.failed_attempts + 1,
                            locked_until = CASE WHEN oc.failed_attempts + 1 >= 5
                                THEN :lockUntil ELSE oc.locked_until END,
                            last_audit_record_id = audit.audit_id
                        FROM audit WHERE oc.id = audit.entity_id
                        """)
                .bind("email", email).bind("lockUntil", now.plusSeconds(900))
                .fetch().rowsUpdated().then();
    }

    private Mono<Void> insertProfile(Long userId, String firstName, String lastName, String phoneNumber) {
        DatabaseClient.GenericExecuteSpec insert = databaseClient.sql("""
                        INSERT INTO buyer_profiles (public_id, user_id, first_name, last_name, phone_number)
                        VALUES (gen_random_uuid(), :userId, :firstName, :lastName, :phoneNumber)
                        """).bind("userId", userId);
        insert = bindNullable(insert, "firstName", firstName);
        insert = bindNullable(insert, "lastName", lastName);
        insert = bindNullable(insert, "phoneNumber", phoneNumber);
        return insert.fetch().rowsUpdated().then();
    }

    private Mono<Void> insertOtp(Long userId, String email, String otp, Instant expiresAt) {
        return databaseClient.sql("""
                        INSERT INTO otp_challenges (public_id, user_id, identity, purpose, code_hash, expires_at)
                        VALUES (gen_random_uuid(), :userId, :email, 'LOGIN', :codeHash, :expiresAt)
                        """).bind("userId", userId).bind("email", email).bind("codeHash", hashOtp(otp))
                .bind("expiresAt", expiresAt).fetch().rowsUpdated().then();
    }

    private Mono<Void> invalidateActive(String email, Instant now) {
        return databaseClient.sql("""
                        WITH prior AS MATERIALIZED (
                            SELECT oc.id, oc.user_id FROM otp_challenges oc
                            JOIN app_users au ON au.id = oc.user_id
                            WHERE LOWER(au.email) = LOWER(:email) AND au.role = 'BUYER'
                              AND oc.purpose = 'LOGIN' AND oc.consumed_at IS NULL FOR UPDATE
                        ), audits AS (
                            INSERT INTO audit_records (public_id, actor_user_id, action, entity_type, entity_id)
                            SELECT gen_random_uuid(), user_id, 'UPDATE', 'otp_challenges', id FROM prior
                            RETURNING id AS audit_id, entity_id
                        )
                        UPDATE otp_challenges oc SET consumed_at = :now,
                            last_audit_record_id = audits.audit_id
                        FROM audits WHERE oc.id = audits.entity_id
                        """).bind("email", email).bind("now", now).fetch().rowsUpdated().then();
    }

    private Mono<Void> insertReplacement(String email, String otp, Instant expiresAt) {
        return databaseClient.sql("""
                        INSERT INTO otp_challenges (public_id, user_id, identity, purpose, code_hash, expires_at)
                        SELECT gen_random_uuid(), id, email, 'LOGIN', :codeHash, :expiresAt
                        FROM app_users WHERE LOWER(email) = LOWER(:email) AND role = 'BUYER'
                        """).bind("email", email).bind("codeHash", hashOtp(otp)).bind("expiresAt", expiresAt)
                .fetch().rowsUpdated().flatMap(rows -> rows == 1 ? Mono.empty()
                        : Mono.error(new IllegalStateException("buyer identity was not found")));
    }

    private String selectSql() {
        return """
                SELECT %s
                FROM app_users au
                JOIN buyer_profiles bp ON bp.user_id = au.id
                LEFT JOIN LATERAL (
                    SELECT code_hash, expires_at, failed_attempts, locked_until
                    FROM otp_challenges
                    WHERE user_id = au.id AND purpose = 'LOGIN' AND consumed_at IS NULL
                    ORDER BY created_at DESC LIMIT 1
                ) oc ON TRUE
                WHERE LOWER(au.email) = LOWER(:email) AND au.role = 'BUYER'
                """.formatted(COLUMNS);
    }

    private static BuyerIdentityRecord map(io.r2dbc.spi.Row row) {
        return new BuyerIdentityRecord(row.get("public_id", java.util.UUID.class), row.get("email", String.class),
                row.get("first_name", String.class), row.get("last_name", String.class),
                row.get("phone_number", String.class), "ACTIVE".equals(row.get("status", String.class)),
                row.get("code_hash", String.class), row.get("expires_at", Instant.class),
                row.get("failed_attempts", Integer.class) == null ? 0 : row.get("failed_attempts", Integer.class),
                row.get("locked_until", Instant.class));
    }

    private String hashOtp(String otp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(otpPepper, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(otp.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", exception);
        }
    }

    private static DatabaseClient.GenericExecuteSpec bindNullable(DatabaseClient.GenericExecuteSpec spec,
                                                                  String name, String value) {
        return value == null ? spec.bindNull(name, String.class) : spec.bind(name, value);
    }

    private static boolean isUniqueViolation(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof DuplicateKeyException
                    || current instanceof R2dbcException r2dbc && "23505".equals(r2dbc.getSqlState())) return true;
        }
        return false;
    }

    private static void requireEmail(String email) {
        if (email == null || email.isBlank()) throw new IllegalArgumentException("email is required");
    }

    private static void requireOtp(String otp, Instant expiresAt) {
        if (otp == null || otp.isBlank()) throw new IllegalArgumentException("otp is required");
        if (expiresAt == null) throw new IllegalArgumentException("otp expiry is required");
    }
}
