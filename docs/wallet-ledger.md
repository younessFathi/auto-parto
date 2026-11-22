# Wallet ledger contract

Wallet mutations are persisted by PostgreSQL in one transaction. The transaction locks the single `(owner, currency)` wallet row, appends an immutable `wallet_ledger` entry with its audit record, updates the minor-unit balance, and stores the replay result in `idempotency_records`.

Every mutation requires an `Idempotency-Key`. The key is scoped to the authenticated owner and operation. The request fingerprint covers currency, signed minor-unit amount, operation, and external reference. Reusing a live key with the same request returns the original result without another ledger entry; reusing it with a different request returns `409 CONFLICT`.

Idempotency records are retained for seven days. A bounded cleanup can remove records whose `expires_at` has passed. The historical ledger entry remains immutable, and a key may be used again only after its retained record has been removed. Insufficient funds also return `409 INSUFFICIENT_BALANCE` at the API boundary.


## Confirmed manual funding authority (AP-0050)

A seller cannot credit their own wallet. Both legacy and versioned seller `/wallet/add` routes return 403. Seller `/wallet/deduct` remains owner-only and requires a strictly positive major-unit amount before converting it into a debit; zero/negative values cannot invert into a credit. The service also refuses nonnegative seller mutations. Existing request keys remain required for debits.

`POST /api/v1/admin/wallet-funding` is the approved manual funding authority. It accepts an existing canonical admin JWT plus an `Idempotency-Key` header and a JSON body containing `beneficiaryUserPublicId` (UUID), `amountMinor` (positive 64-bit minor units), `currency` (supported uppercase code), `fundingReference` (1–255 ASCII letters/digits with `.`, `_`, `:`, `/`, `-` after the first character), and `confirmed: true`. The administrator is attesting a confirmed human-recorded funding event, not invoking a payment provider or proving external settlement. No real admin identity is provisioned by this feature.

The transaction rechecks persisted ACTIVE ADMIN authority and an ACTIVE SELLER beneficiary, holding their eligibility against concurrent deactivation. One globally unique, case-sensitive `fundingReference` binds beneficiary, amount/currency fingerprint, the original administrator, wallet/ledger IDs, resulting balance/version and an actual-administrator audit. Evidence/result, wallet mutation and both audits commit together or all roll back. Completed funding evidence cannot be updated or deleted. References must be non-secret business identifiers; never include credentials or raw provider payloads.

The funding result is permanent and has no seven-day cleanup. An identical reference/details replay returns the exact original snapshot even with a different client key, after ordinary key expiry/cleanup or after a process restart; it may differ from the wallet's later balance. Reference reuse with a different beneficiary, amount or currency returns 409. A fresh reference cannot claim a still-retained wallet request key used for another mutation. Validation/authority/storage failure does not consume a funding reference or report success; after an uncertain client response, retry the same reference and details rather than inventing a new funding event. Authority and beneficiary eligibility are rechecked on replay too.
