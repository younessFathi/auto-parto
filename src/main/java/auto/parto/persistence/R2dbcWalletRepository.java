package auto.parto.persistence;

import auto.parto.enums.Currency;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
@ConditionalOnProperty(prefix = "auto.parto.persistence.postgresql.wallet", name = "enabled",
        havingValue = "true")
public class R2dbcWalletRepository implements WalletRepository {

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
        return transactions.transactional(ensureWallet(ownerUserPublicId, currency)
                .then(lockWallet(ownerUserPublicId, currency))
                .flatMap(wallet -> existingMutation(wallet, idempotencyKey)
                        .switchIfEmpty(Mono.defer(() -> applyMutation(
                                wallet, amountMinor, operation, idempotencyKey, externalReference)))));
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

    private Mono<WalletMutationResult> existingMutation(WalletRecord wallet, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) return Mono.empty();
        return databaseClient.sql("""
                        SELECT ledger.public_id AS ledger_public_id,
                               wallet.public_id, owner.public_id AS owner_public_id,
                               wallet.balance_minor, wallet.currency, wallet.version
                        FROM wallet_ledger ledger
                        JOIN wallets wallet ON wallet.id = ledger.wallet_id
                        JOIN app_users owner ON owner.id = wallet.owner_user_id
                        WHERE ledger.wallet_id = (
                            SELECT id FROM wallets WHERE public_id = :walletPublicId
                        ) AND ledger.idempotency_key = :idempotencyKey
                        """)
                .bind("walletPublicId", wallet.publicId())
                .bind("idempotencyKey", idempotencyKey)
                .map((row, metadata) -> new WalletMutationResult(
                        mapWallet(row), row.get("ledger_public_id", UUID.class), false))
                .one();
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
}
