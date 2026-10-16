package auto.parto.persistence;

import auto.parto.enums.Currency;
import auto.parto.exception.ConflictException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Repository
@ConditionalOnProperty(prefix = "auto.parto.persistence.postgresql.wallet", name = "enabled",
        havingValue = "true")
public class R2dbcWalletRepository implements WalletRepository {

    static final Duration IDEMPOTENCY_RETENTION = Duration.ofDays(7);
    private static final int MAX_PURGE_LIMIT = 1_000;

    private final DatabaseClient databaseClient;
    private final TransactionalOperator transactions;

    public R2dbcWalletRepository(DatabaseClient databaseClient, TransactionalOperator transactions) {
        this.databaseClient = databaseClient;
        this.transactions = transactions;
    }

    @Override
    public Mono<WalletRecord> findByOwnerPublicId(UUID ownerUserPublicId, String currency) {
        requirePublicId(ownerUserPublicId, "ownerUserPublicId");
        requireCurrency(currency);
        return databaseClient.sql(selectWalletSql() + " WHERE owner.public_id = :ownerPublicId AND wallet.currency = :currency")
                .bind("ownerPublicId", ownerUserPublicId)
                .bind("currency", currency)
                .map((row, metadata) -> mapWallet(row))
                .one();
    }

    @Override
    public Mono<WalletMutationResult> mutate(UUID ownerUserPublicId, String currency, long amountMinor,
                                             String operation, String idempotencyKey, String externalReference) {
        requirePublicId(ownerUserPublicId, "ownerUserPublicId");
        requireCurrency(currency);
        requireOperation(operation, amountMinor);
        requireIdempotencyKey(idempotencyKey);
        requireOptionalText(externalReference, "externalReference");

        String fingerprint = requestFingerprint(currency, amountMinor, operation, externalReference);
        Instant now = Instant.now();
        Instant expiresAt = now.plus(IDEMPOTENCY_RETENTION);
        return transactions.transactional(deleteExpiredKey(ownerUserPublicId, operation, idempotencyKey, now)
                .then(ensureWallet(ownerUserPublicId, currency))
                .then(lockWallet(ownerUserPublicId, currency))
                .flatMap(wallet -> reserveOrReplay(ownerUserPublicId, operation, idempotencyKey,
                                fingerprint, now, expiresAt)
                        .flatMap(decision -> decision.replayed()
                                ? Mono.just(decision.result())
                                : applyMutation(wallet, amountMinor, operation, idempotencyKey, externalReference)
                                .flatMap(result -> completeIdempotency(ownerUserPublicId, operation,
                                        idempotencyKey, fingerprint, result).thenReturn(result)))));
    }

    @Override
    public Mono<Long> purgeExpiredIdempotencyRecords(Instant now, int limit) {
        if (now == null) throw new IllegalArgumentException("now is required");
        if (limit < 1 || limit > MAX_PURGE_LIMIT) {
            throw new IllegalArgumentException("purge limit must be between 1 and 1000");
        }
        return databaseClient.sql("""
                        DELETE FROM idempotency_records
                        WHERE id IN (
                            SELECT id FROM idempotency_records
                            WHERE expires_at <= :now
                            ORDER BY expires_at, id
                            LIMIT :limit
                        )
                        """)
                .bind("now", now)
                .bind("limit", limit)
                .fetch().rowsUpdated().map(rows -> rows);
    }

    private Mono<Void> deleteExpiredKey(UUID ownerUserPublicId, String operation, String idempotencyKey,
                                         Instant now) {
        return databaseClient.sql("""
                        DELETE FROM idempotency_records record
                        USING app_users owner
                        WHERE record.owner_user_id = owner.id
                          AND owner.public_id = :ownerPublicId
                          AND record.operation = :operation
                          AND record.idempotency_key = :idempotencyKey
                          AND record.expires_at <= :now
                        """)
                .bind("ownerPublicId", ownerUserPublicId)
                .bind("operation", operation)
                .bind("idempotencyKey", idempotencyKey)
                .bind("now", now)
                .fetch().rowsUpdated().then();
    }

    private Mono<Void> ensureWallet(UUID ownerUserPublicId, String currency) {
        return databaseClient.sql("""
                        INSERT INTO wallets (public_id, owner_user_id, balance_minor, currency)
                        SELECT gen_random_uuid(), id, 0, :currency
                        FROM app_users
                        WHERE public_id = :ownerPublicId AND status = 'ACTIVE'
                        ON CONFLICT (owner_user_id, currency) DO NOTHING
                        """)
                .bind("ownerPublicId", ownerUserPublicId)
                .bind("currency", currency)
                .fetch().rowsUpdated().then();
    }

    private Mono<WalletRecord> lockWallet(UUID ownerUserPublicId, String currency) {
        return databaseClient.sql(selectWalletSql()
                        + " WHERE owner.public_id = :ownerPublicId AND wallet.currency = :currency FOR UPDATE")
                .bind("ownerPublicId", ownerUserPublicId)
                .bind("currency", currency)
                .map((row, metadata) -> mapWallet(row))
                .one();
    }

    private Mono<IdempotencyDecision> reserveOrReplay(UUID ownerUserPublicId, String operation,
                                                       String idempotencyKey, String fingerprint,
                                                       Instant now, Instant expiresAt) {
        return findIdempotency(ownerUserPublicId, operation, idempotencyKey, fingerprint, now)
                .switchIfEmpty(Mono.defer(() -> insertIdempotency(ownerUserPublicId, operation,
                                idempotencyKey, fingerprint, now, expiresAt)
                        .flatMap(inserted -> inserted
                                ? Mono.just(IdempotencyDecision.newReservation())
                                : findIdempotency(ownerUserPublicId, operation, idempotencyKey, fingerprint, now)
                                .switchIfEmpty(Mono.error(new ConflictException("Idempotency key is unavailable"))))));
    }

    private Mono<IdempotencyDecision> findIdempotency(UUID ownerUserPublicId, String operation,
                                                       String idempotencyKey, String fingerprint, Instant now) {
        return databaseClient.sql("""
                        SELECT record.request_fingerprint, record.wallet_public_id, record.ledger_public_id,
                               record.wallet_balance_after_minor, record.wallet_currency, record.wallet_version
                        FROM idempotency_records record
                        JOIN app_users owner ON owner.id = record.owner_user_id
                        WHERE owner.public_id = :ownerPublicId
                          AND record.operation = :operation
                          AND record.idempotency_key = :idempotencyKey
                          AND record.expires_at > :now
                        """)
                .bind("ownerPublicId", ownerUserPublicId)
                .bind("operation", operation)
                .bind("idempotencyKey", idempotencyKey)
                .bind("now", now)
                .map((row, metadata) -> new StoredIdempotency(
                        row.get("request_fingerprint", String.class),
                        row.get("wallet_public_id", UUID.class),
                        row.get("ledger_public_id", UUID.class),
                        row.get("wallet_balance_after_minor", Long.class),
                        row.get("wallet_currency", String.class),
                        row.get("wallet_version", Long.class)))
                .one()
                .flatMap(stored -> {
                    if (!stored.requestFingerprint().equals(fingerprint)) {
                        return Mono.error(new ConflictException("Idempotency key was reused for a different request"));
                    }
                    if (stored.walletPublicId() == null || stored.ledgerPublicId() == null) {
                        return Mono.error(new ConflictException("Idempotency request is still in progress"));
                    }
                    WalletRecord wallet = new WalletRecord(stored.walletPublicId(), ownerUserPublicId,
                            stored.walletBalanceAfterMinor(), stored.walletCurrency(), stored.walletVersion());
                    return Mono.just(new IdempotencyDecision(true,
                            new WalletMutationResult(wallet, stored.ledgerPublicId(), false)));
                });
    }

    private Mono<Boolean> insertIdempotency(UUID ownerUserPublicId, String operation, String idempotencyKey,
                                            String fingerprint, Instant now, Instant expiresAt) {
        return databaseClient.sql("""
                        INSERT INTO idempotency_records (owner_user_id, operation, idempotency_key,
                                                         request_fingerprint, created_at, expires_at)
                        SELECT id, :operation, :idempotencyKey, :fingerprint, :now, :expiresAt
                        FROM app_users
                        WHERE public_id = :ownerPublicId
                        ON CONFLICT (owner_user_id, operation, idempotency_key) DO NOTHING
                        """)
                .bind("ownerPublicId", ownerUserPublicId)
                .bind("operation", operation)
                .bind("idempotencyKey", idempotencyKey)
                .bind("fingerprint", fingerprint)
                .bind("now", now)
                .bind("expiresAt", expiresAt)
                .fetch().rowsUpdated().map(rows -> rows == 1);
    }

    private Mono<Void> completeIdempotency(UUID ownerUserPublicId, String operation, String idempotencyKey,
                                            String fingerprint, WalletMutationResult result) {
        return databaseClient.sql("""
                        UPDATE idempotency_records record
                        SET response_status = 200,
                            wallet_public_id = :walletPublicId,
                            ledger_public_id = :ledgerPublicId,
                            wallet_balance_after_minor = :balanceAfterMinor,
                            wallet_currency = :currency,
                            wallet_version = :version
                        FROM app_users owner
                        WHERE record.owner_user_id = owner.id
                          AND owner.public_id = :ownerPublicId
                          AND record.operation = :operation
                          AND record.idempotency_key = :idempotencyKey
                          AND record.request_fingerprint = :fingerprint
                        """)
                .bind("ownerPublicId", ownerUserPublicId)
                .bind("operation", operation)
                .bind("idempotencyKey", idempotencyKey)
                .bind("fingerprint", fingerprint)
                .bind("walletPublicId", result.wallet().publicId())
                .bind("ledgerPublicId", result.ledgerPublicId())
                .bind("balanceAfterMinor", result.wallet().balanceMinor())
                .bind("currency", result.wallet().currency())
                .bind("version", result.wallet().version())
                .fetch().rowsUpdated()
                .flatMap(rows -> rows == 1 ? Mono.empty()
                        : Mono.error(new IllegalStateException("wallet idempotency record was not completed")));
    }

    private Mono<WalletMutationResult> applyMutation(WalletRecord wallet, long amountMinor,
                                                      String operation, String idempotencyKey,
                                                      String externalReference) {
        final long newBalance;
        try {
            newBalance = Math.addExact(wallet.balanceMinor(), amountMinor);
        } catch (ArithmeticException overflow) {
            return Mono.error(new IllegalArgumentException("wallet balance overflow", overflow));
        }
        if (newBalance < 0) return Mono.error(new IllegalStateException("insufficient wallet balance"));

        UUID ledgerPublicId = UUID.randomUUID();
        return nextLedgerId().flatMap(ledgerId -> insertAuditRecord(wallet, ledgerId)
                .flatMap(auditId -> insertLedger(ledgerId, ledgerPublicId, wallet, amountMinor,
                        newBalance, operation, idempotencyKey, externalReference, auditId))
                .then(updateWallet(wallet, newBalance))
                .thenReturn(new WalletMutationResult(
                        new WalletRecord(wallet.publicId(), wallet.ownerUserPublicId(), newBalance,
                                wallet.currency(), wallet.version() + 1),
                        ledgerPublicId, true)));
    }

    private Mono<Long> nextLedgerId() {
        return databaseClient.sql("SELECT nextval('wallet_ledger_id_seq') AS ledger_id")
                .map((row, metadata) -> row.get("ledger_id", Long.class))
                .one();
    }

    private Mono<Long> insertAuditRecord(WalletRecord wallet, long ledgerId) {
        return databaseClient.sql("""
                        INSERT INTO audit_records (public_id, actor_user_id, action, entity_type, entity_id)
                        SELECT gen_random_uuid(), owner.id, 'CREATE', 'wallet_ledger', :ledgerId
                        FROM app_users owner WHERE owner.public_id = :ownerPublicId
                        RETURNING id
                        """)
                .bind("ledgerId", ledgerId)
                .bind("ownerPublicId", wallet.ownerUserPublicId())
                .map((row, metadata) -> row.get("id", Long.class))
                .one();
    }

    private Mono<Void> insertLedger(long ledgerId, UUID ledgerPublicId, WalletRecord wallet,
                                    long amountMinor, long balanceAfterMinor, String operation,
                                    String idempotencyKey, String externalReference, long auditId) {
        DatabaseClient.GenericExecuteSpec insert = databaseClient.sql("""
                        INSERT INTO wallet_ledger (id, public_id, wallet_id, amount_minor, balance_after_minor,
                                                   currency, operation, external_reference, idempotency_key, audit_record_id)
                        SELECT :ledgerId, :ledgerPublicId, id, :amountMinor, :balanceAfterMinor,
                               currency, :operation, :externalReference, :idempotencyKey, :auditId
                        FROM wallets WHERE public_id = :walletPublicId
                        """)
                .bind("ledgerId", ledgerId)
                .bind("ledgerPublicId", ledgerPublicId)
                .bind("amountMinor", amountMinor)
                .bind("balanceAfterMinor", balanceAfterMinor)
                .bind("operation", operation)
                .bind("auditId", auditId)
                .bind("walletPublicId", wallet.publicId());
        insert = bindNullable(insert, "externalReference", externalReference, String.class);
        insert = bindNullable(insert, "idempotencyKey", idempotencyKey, String.class);
        return insert.fetch().rowsUpdated().then();
    }

    private Mono<Void> updateWallet(WalletRecord wallet, long newBalance) {
        return databaseClient.sql("""
                        UPDATE wallets SET balance_minor = :balanceMinor, version = version + 1
                        WHERE public_id = :walletPublicId AND version = :version
                        """)
                .bind("balanceMinor", newBalance)
                .bind("walletPublicId", wallet.publicId())
                .bind("version", wallet.version())
                .fetch().rowsUpdated()
                .flatMap(rows -> rows == 1 ? Mono.empty()
                        : Mono.error(new IllegalStateException("wallet version changed unexpectedly")));
    }

    private static String selectWalletSql() {
        return """
                SELECT wallet.public_id, owner.public_id AS owner_public_id,
                       wallet.balance_minor, wallet.currency, wallet.version
                FROM wallets wallet JOIN app_users owner ON owner.id = wallet.owner_user_id
                """;
    }

    private static WalletRecord mapWallet(io.r2dbc.spi.Row row) {
        return new WalletRecord(row.get("public_id", UUID.class),
                row.get("owner_public_id", UUID.class), row.get("balance_minor", Long.class),
                row.get("currency", String.class), row.get("version", Long.class));
    }

    private static <T> DatabaseClient.GenericExecuteSpec bindNullable(
            DatabaseClient.GenericExecuteSpec spec, String name, T value, Class<T> type) {
        return value == null ? spec.bindNull(name, type) : spec.bind(name, value);
    }

    private static void requirePublicId(UUID value, String name) {
        if (value == null) throw new IllegalArgumentException(name + " is required");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }

    private static void requireOptionalText(String value, String name) {
        if (value != null && value.length() > 255) throw new IllegalArgumentException(name + " is too long");
    }

    private static void requireIdempotencyKey(String value) {
        requireText(value, "idempotencyKey");
        if (value.length() > 255) throw new IllegalArgumentException("idempotencyKey is too long");
    }

    private static void requireCurrency(String value) {
        Currency.requireSupportedCode(value);
    }

    private static void requireOperation(String operation, long amountMinor) {
        requireText(operation, "operation");
        boolean valid = switch (operation) {
            case "CREDIT", "REFUND" -> amountMinor > 0;
            case "DEBIT" -> amountMinor < 0;
            case "ADJUSTMENT" -> amountMinor != 0;
            default -> false;
        };
        if (!valid) throw new IllegalArgumentException("operation and amount sign are inconsistent");
    }

    static String requestFingerprint(String currency, long amountMinor, String operation,
                                     String externalReference) {
        String canonical = field(currency) + field(Long.toString(amountMinor))
                + field(operation) + field(externalReference == null ? "<null>" : externalReference);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String field(String value) {
        return value.length() + ":" + value + "|";
    }

    private record IdempotencyDecision(boolean replayed, WalletMutationResult result) {
        private static IdempotencyDecision newReservation() {
            return new IdempotencyDecision(false, null);
        }
    }

    private record StoredIdempotency(String requestFingerprint, UUID walletPublicId, UUID ledgerPublicId,
                                     Long walletBalanceAfterMinor, String walletCurrency, Long walletVersion) {
    }
}
