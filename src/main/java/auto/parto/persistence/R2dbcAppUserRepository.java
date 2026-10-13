package auto.parto.persistence;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

import java.util.UUID;

@Repository
@ConditionalOnProperty(prefix = "auto.parto.persistence.postgresql", name = "enabled", havingValue = "true")
public final class R2dbcAppUserRepository implements AppUserRepository {

    private static final String USER_COLUMNS = """
            public_id, provider, provider_subject, email, role, status,
            key_version, created_at, updated_at
            """;

    private final DatabaseClient databaseClient;
    private final TransactionalOperator transactionalOperator;

    public R2dbcAppUserRepository(DatabaseClient databaseClient,
                                  TransactionalOperator transactionalOperator) {
        this.databaseClient = databaseClient;
        this.transactionalOperator = transactionalOperator;
    }

    @Override
    public Mono<AppUserRecord> findByPublicId(UUID publicId) {
        if (publicId == null) {
            return Mono.error(new IllegalArgumentException("publicId is required"));
        }
        return databaseClient.sql("SELECT " + USER_COLUMNS + " FROM app_users WHERE public_id = :publicId")
                .bind("publicId", publicId)
                .map((row, metadata) -> mapUser(row))
                .one();
    }

    @Override
    public Mono<AppUserRecord> findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Mono.error(new IllegalArgumentException("email is required"));
        }
        return databaseClient.sql("SELECT " + USER_COLUMNS
                        + " FROM app_users WHERE LOWER(email) = LOWER(:email)")
                .bind("email", email)
                .map((row, metadata) -> mapUser(row))
                .one();
    }

    @Override
    public Mono<AppUserRecord> findByProviderSubject(String provider, String providerSubject) {
        if (provider == null || provider.isBlank()) {
            return Mono.error(new IllegalArgumentException("provider is required"));
        }
        if (providerSubject == null || providerSubject.isBlank()) {
            return Mono.error(new IllegalArgumentException("providerSubject is required"));
        }
        return databaseClient.sql("SELECT " + USER_COLUMNS
                        + " FROM app_users WHERE provider = :provider AND provider_subject = :providerSubject")
                .bind("provider", provider)
                .bind("providerSubject", providerSubject)
                .map((row, metadata) -> mapUser(row))
                .one();
    }

    @Override
    public Mono<AppUserRecord> create(AppUserRecord user) {
        validateUser(user);
        DatabaseClient.GenericExecuteSpec insert = databaseClient.sql("""
                                INSERT INTO app_users
                                    (public_id, provider, provider_subject, email, role, status, key_version)
                                VALUES
                                    (:publicId, :provider, :providerSubject, :email, :role, :status, :keyVersion)
                                RETURNING
                                """ + USER_COLUMNS)
                .bind("publicId", user.publicId())
                .bind("role", user.role())
                .bind("status", user.status());
        insert = bindNullable(insert, "provider", user.provider());
        insert = bindNullable(insert, "providerSubject", user.providerSubject());
        insert = bindNullable(insert, "email", user.email());
        insert = bindNullable(insert, "keyVersion", user.keyVersion());
        return transactionalOperator.transactional(insert
                .map((row, metadata) -> mapUser(row))
                .one());
    }

    @Override
    public Flux<UUID> findPublicIdsByRole(String role, int limit) {
        if (role == null || role.isBlank()) throw new IllegalArgumentException("role is required");
        if (limit < 1 || limit > 1000) throw new IllegalArgumentException("limit must be between 1 and 1000");
        return databaseClient.sql("""
                        SELECT public_id FROM app_users
                        WHERE role = :role AND status = 'ACTIVE'
                        ORDER BY created_at, id
                        LIMIT :limit
                        """)
                .bind("role", role).bind("limit", limit)
                .map((row, metadata) -> row.get("public_id", UUID.class)).all();
    }

    @Override
    public Mono<AppUserRecord> updateStatus(UUID publicId, String status) {
        if (publicId == null) throw new IllegalArgumentException("publicId is required");
        if (status == null || status.isBlank()) throw new IllegalArgumentException("status is required");
        return transactionalOperator.transactional(databaseClient.sql("""
                        UPDATE app_users SET status = :status
                        WHERE public_id = :publicId
                        RETURNING
                        """ + USER_COLUMNS)
                .bind("publicId", publicId).bind("status", status)
                .map((row, metadata) -> mapUser(row)).one());
    }

    private static DatabaseClient.GenericExecuteSpec bindNullable(
            DatabaseClient.GenericExecuteSpec spec, String name, String value) {
        return value == null ? spec.bindNull(name, String.class) : spec.bind(name, value);
    }

    private static AppUserRecord mapUser(io.r2dbc.spi.Row row) {
        return new AppUserRecord(
                row.get("public_id", UUID.class),
                row.get("provider", String.class),
                row.get("provider_subject", String.class),
                row.get("email", String.class),
                row.get("role", String.class),
                row.get("status", String.class),
                row.get("key_version", String.class),
                row.get("created_at", java.time.Instant.class),
                row.get("updated_at", java.time.Instant.class));
    }

    private static void validateUser(AppUserRecord user) {
        if (user == null || user.publicId() == null || user.role() == null || user.role().isBlank()
                || user.status() == null || user.status().isBlank()
                || (user.provider() == null) != (user.providerSubject() == null)
                || (user.email() == null && user.providerSubject() == null)) {
            throw new IllegalArgumentException("publicId, role, status, and an identity are required");
        }
    }
}
