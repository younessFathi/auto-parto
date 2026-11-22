package auto.parto.persistence;

import auto.parto.enums.Currency;
import auto.parto.exception.ConflictException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.UUID;

@Repository
@ConditionalOnProperty(prefix = "auto.parto.persistence.postgresql.wallet", name = "enabled", havingValue = "true")
public class R2dbcWalletFundingRepository implements WalletFundingRepository {
    private final DatabaseClient database;
    private final WalletRepository wallets;
    private final TransactionalOperator transactions;

    public R2dbcWalletFundingRepository(DatabaseClient databaseClient, WalletRepository wallets,
                                        TransactionalOperator transactions) {
        this.database = databaseClient;
        this.wallets = wallets;
        this.transactions = transactions;
    }

    @Override
    public Mono<WalletFundingResult> confirm(UUID administratorPublicId, UUID beneficiaryPublicId,
            long amountMinor, String currency, String fundingReference, String idempotencyKey) {
        Objects.requireNonNull(administratorPublicId, "administratorPublicId");
        Objects.requireNonNull(beneficiaryPublicId, "beneficiaryPublicId");
        Currency.requireSupportedCode(currency);
        if (amountMinor <= 0) throw new IllegalArgumentException("Funding amount must be positive");
        if (fundingReference == null || !fundingReference.matches("[A-Za-z0-9][A-Za-z0-9._:/-]{0,254}")) {
            throw new IllegalArgumentException("Invalid funding reference");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 255) {
            throw new IllegalArgumentException("Idempotency-Key is required and limited to 255 characters");
        }
        String fingerprint = R2dbcWalletRepository.requestFingerprint(currency, amountMinor,
                "CREDIT", beneficiaryPublicId.toString());
        return Mono.defer(() -> requireIdentity(administratorPublicId, "ADMIN", HttpStatus.FORBIDDEN)
                .then(requireIdentity(beneficiaryPublicId, "SELLER", HttpStatus.NOT_FOUND))
                .then(reserve(administratorPublicId, beneficiaryPublicId, amountMinor, currency,
                        fundingReference, fingerprint))
                .flatMap(inserted -> inserted
                        ? apply(beneficiaryPublicId, amountMinor, currency, fundingReference, idempotencyKey)
                        : replay(fundingReference, fingerprint)))
                .as(transactions::transactional);
    }

    /** Lock identity status/role against concurrent deactivation until the credit commits. */
    private Mono<Void> requireIdentity(UUID publicId, String role, HttpStatus denial) {
        return database.sql("""
                SELECT public_id FROM app_users
                WHERE public_id = :publicId AND role = :role AND status = 'ACTIVE'
                FOR SHARE
                """).bind("publicId", publicId).bind("role", role)
                .map((row, metadata) -> row.get("public_id", UUID.class)).one()
                .switchIfEmpty(Mono.error(new ResponseStatusException(denial, "Funding identity is not eligible")))
                .then();
    }

    private Mono<Boolean> reserve(UUID admin, UUID beneficiary, long amount, String currency,
                                   String reference, String fingerprint) {
        return database.sql("""
                INSERT INTO wallet_funding_events
                    (public_id, funding_reference, administrator_user_id, beneficiary_user_id,
                     amount_minor, currency, request_fingerprint)
                SELECT gen_random_uuid(), :reference, admin.id, beneficiary.id, :amount, :currency, :fingerprint
                FROM app_users admin CROSS JOIN app_users beneficiary
                WHERE admin.public_id = :admin AND beneficiary.public_id = :beneficiary
                ON CONFLICT (funding_reference) DO NOTHING
                """).bind("admin", admin).bind("beneficiary", beneficiary).bind("amount", amount)
                .bind("currency", currency).bind("reference", reference).bind("fingerprint", fingerprint)
                .fetch().rowsUpdated().map(count -> count == 1);
    }

    private Mono<WalletFundingResult> apply(UUID beneficiary, long amount, String currency,
                                           String reference, String key) {
        return wallets.mutate(beneficiary, currency, amount, "CREDIT", key, reference)
                .switchIfEmpty(Mono.error(new ConflictException("Funding beneficiary unavailable")))
                .flatMap(result -> {
                    // A completed wallet-key replay cannot be reassigned to a fresh funding event.
                    if (!result.applied()) return Mono.error(new ConflictException("Funding request key already used"));
                    return database.sql("""
                            WITH funding_audit AS (
                                INSERT INTO audit_records (public_id, actor_user_id, action, entity_type, entity_id)
                                SELECT gen_random_uuid(), administrator_user_id, 'CREATE', 'wallet_funding', id
                                FROM wallet_funding_events
                                WHERE funding_reference = :reference AND completed_at IS NULL
                                RETURNING id
                            )
                            UPDATE wallet_funding_events
                            SET wallet_public_id = :wallet, ledger_public_id = :ledger,
                                balance_after_minor = :balance, wallet_version = :version,
                                completed_at = CURRENT_TIMESTAMP,
                                audit_record_id = (SELECT id FROM funding_audit)
                            WHERE funding_reference = :reference AND completed_at IS NULL
                            """).bind("wallet", result.wallet().publicId()).bind("ledger", result.ledgerPublicId())
                            .bind("balance", result.wallet().balanceMinor()).bind("version", result.wallet().version())
                            .bind("reference", reference).fetch().rowsUpdated()
                            .flatMap(count -> count == 1 ? read(reference).map(StoredFunding::result)
                                    : Mono.error(new ConflictException("Funding completion failed")));
                });
    }

    private Mono<WalletFundingResult> replay(String reference, String fingerprint) {
        return read(reference).flatMap(stored -> {
            if (!fingerprint.equals(stored.fingerprint())) {
                return Mono.error(new ConflictException("Funding reference was reused for different details"));
            }
            if (stored.result() == null) return Mono.error(new ConflictException("Funding is not completed"));
            return Mono.just(stored.result());
        });
    }

    private Mono<StoredFunding> read(String reference) {
        return database.sql("""
                SELECT event.*, beneficiary.public_id AS beneficiary_public_id
                FROM wallet_funding_events event JOIN app_users beneficiary ON beneficiary.id = event.beneficiary_user_id
                WHERE event.funding_reference = :reference
                """).bind("reference", reference).map((row, metadata) -> {
                    WalletFundingResult result = row.get("completed_at", java.time.Instant.class) == null ? null
                            : new WalletFundingResult(row.get("public_id", UUID.class), reference,
                            row.get("beneficiary_public_id", UUID.class), row.get("amount_minor", Long.class),
                            row.get("currency", String.class), row.get("wallet_public_id", UUID.class),
                            row.get("ledger_public_id", UUID.class), row.get("balance_after_minor", Long.class),
                            row.get("wallet_version", Long.class));
                    return new StoredFunding(row.get("request_fingerprint", String.class), result);
                }).one().switchIfEmpty(Mono.error(new ConflictException("Funding result unavailable")));
    }

    private record StoredFunding(String fingerprint, WalletFundingResult result) { }
}
