package auto.parto.persistence;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Repository
@ConditionalOnProperty(prefix = "auto.parto.persistence.postgresql.device-tokens", name = "enabled",
        havingValue = "true")
public class R2dbcDeviceTokenRepository implements DeviceTokenRepository {

    private static final String SELECT = """
            SELECT token.public_id, owner.public_id AS owner_public_id, token.provider_token,
                   token.platform, token.status, token.created_at, token.last_seen_at,
                   token.invalidated_at
            FROM device_tokens token
            JOIN app_users owner ON owner.id = token.owner_user_id
            """;

    private final DatabaseClient databaseClient;
    private final TransactionalOperator transactions;

    public R2dbcDeviceTokenRepository(DatabaseClient databaseClient, TransactionalOperator transactions) {
        this.databaseClient = databaseClient;
        this.transactions = transactions;
    }

    @Override
    public Mono<DeviceTokenRecord> register(UUID ownerUserPublicId, String providerToken, String platform) {
        requirePublicId(ownerUserPublicId, "ownerUserPublicId");
        requireText(providerToken, "providerToken");
        String normalizedPlatform = normalizePlatform(platform);
        return transactions.transactional(findByProviderToken(providerToken)
                .flatMap(existing -> updateExisting(existing, ownerUserPublicId, normalizedPlatform))
                .switchIfEmpty(Mono.defer(() -> insertNew(ownerUserPublicId, providerToken, normalizedPlatform))));
    }

    @Override
    public Flux<DeviceTokenRecord> findActiveByOwnerPublicId(UUID ownerUserPublicId, int limit) {
        requirePublicId(ownerUserPublicId, "ownerUserPublicId");
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit must be between 1 and 100");
        return databaseClient.sql(SELECT + """
                        WHERE owner.public_id = :ownerPublicId AND token.status = 'ACTIVE'
                        ORDER BY token.last_seen_at DESC
                        LIMIT :limit
                        """)
                .bind("ownerPublicId", ownerUserPublicId)
                .bind("limit", limit)
                .map((row, metadata) -> map(row))
                .all();
    }

    @Override
    public Mono<Boolean> invalidate(String providerToken) {
        requireText(providerToken, "providerToken");
        return transactions.transactional(findByProviderToken(providerToken)
                .filter(token -> "ACTIVE".equals(token.status()))
                .flatMap(token -> insertAudit(token.publicId())
                        .flatMap(auditId -> databaseClient.sql("""
                                UPDATE device_tokens
                                SET status = 'INVALID', invalidated_at = CURRENT_TIMESTAMP,
                                    last_audit_record_id = :auditId
                                WHERE public_id = :publicId AND status = 'ACTIVE'
                                """)
                                .bind("auditId", auditId)
                                .bind("publicId", token.publicId())
                                .fetch().rowsUpdated()))
                .map(rows -> rows == 1)
                .defaultIfEmpty(false));
    }

    private Mono<DeviceTokenRecord> findByProviderToken(String providerToken) {
        return databaseClient.sql(SELECT + " WHERE token.provider_token = :providerToken FOR UPDATE")
                .bind("providerToken", providerToken)
                .map((row, metadata) -> map(row))
                .one();
    }

    private Mono<DeviceTokenRecord> insertNew(UUID ownerUserPublicId, String providerToken, String platform) {
        return databaseClient.sql("""
                        INSERT INTO device_tokens (public_id, owner_user_id, provider_token, platform)
                        SELECT gen_random_uuid(), id, :providerToken, :platform
                        FROM app_users
                        WHERE public_id = :ownerPublicId AND status = 'ACTIVE'
                        RETURNING public_id
                        """)
                .bind("ownerPublicId", ownerUserPublicId)
                .bind("providerToken", providerToken)
                .bind("platform", platform)
                .map((row, metadata) -> row.get("public_id", UUID.class))
                .one()
                .flatMap(this::findByPublicId);
    }

    private Mono<DeviceTokenRecord> updateExisting(DeviceTokenRecord existing, UUID ownerUserPublicId,
                                                   String platform) {
        return findOwnerId(ownerUserPublicId)
                .flatMap(ownerId -> insertAudit(existing.publicId())
                        .flatMap(auditId -> databaseClient.sql("""
                                UPDATE device_tokens
                                SET owner_user_id = :ownerId, platform = :platform, status = 'ACTIVE',
                                    invalidated_at = NULL, last_seen_at = CURRENT_TIMESTAMP,
                                    last_audit_record_id = :auditId
                                WHERE public_id = :publicId
                                """)
                                .bind("ownerId", ownerId)
                                .bind("platform", platform)
                                .bind("auditId", auditId)
                                .bind("publicId", existing.publicId())
                                .fetch().rowsUpdated()
                                .then(findByPublicId(existing.publicId()))));
    }

    private Mono<Long> findOwnerId(UUID ownerUserPublicId) {
        return databaseClient.sql("SELECT id FROM app_users WHERE public_id = :ownerPublicId AND status = 'ACTIVE'")
                .bind("ownerPublicId", ownerUserPublicId)
                .map((row, metadata) -> row.get("id", Long.class))
                .one();
    }

    private Mono<Long> insertAudit(UUID tokenPublicId) {
        return databaseClient.sql("""
                        INSERT INTO audit_records (public_id, action, entity_type, entity_id)
                        SELECT gen_random_uuid(), 'UPDATE', 'device_tokens', token.id
                        FROM device_tokens token WHERE token.public_id = :publicId
                        RETURNING id
                        """)
                .bind("publicId", tokenPublicId)
                .map((row, metadata) -> row.get("id", Long.class))
                .one();
    }

    private Mono<DeviceTokenRecord> findByPublicId(UUID publicId) {
        return databaseClient.sql(SELECT + " WHERE token.public_id = :publicId")
                .bind("publicId", publicId)
                .map((row, metadata) -> map(row))
                .one();
    }

    private static DeviceTokenRecord map(io.r2dbc.spi.Row row) {
        return new DeviceTokenRecord(row.get("public_id", UUID.class),
                row.get("owner_public_id", UUID.class), row.get("provider_token", String.class),
                row.get("platform", String.class), row.get("status", String.class),
                row.get("created_at", Instant.class), row.get("last_seen_at", Instant.class),
                row.get("invalidated_at", Instant.class));
    }

    private static String normalizePlatform(String platform) {
        requireText(platform, "platform");
        String normalized = platform.toUpperCase(java.util.Locale.ROOT);
        if (!java.util.Set.of("ANDROID", "IOS", "WEB").contains(normalized)) {
            throw new IllegalArgumentException("unsupported platform");
        }
        return normalized;
    }

    private static void requirePublicId(UUID value, String name) {
        if (value == null) throw new IllegalArgumentException(name + " is required");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }
}
