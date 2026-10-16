# Wallet ledger contract

Wallet mutations are persisted by PostgreSQL in one transaction. The transaction locks the single `(owner, currency)` wallet row, appends an immutable `wallet_ledger` entry with its audit record, updates the minor-unit balance, and stores the replay result in `idempotency_records`.

Every mutation requires an `Idempotency-Key`. The key is scoped to the authenticated owner and operation. The request fingerprint covers currency, signed minor-unit amount, operation, and external reference. Reusing a live key with the same request returns the original result without another ledger entry; reusing it with a different request returns `409 CONFLICT`.

Idempotency records are retained for seven days. A bounded cleanup can remove records whose `expires_at` has passed. The historical ledger entry remains immutable, and a key may be used again only after its retained record has been removed. Insufficient funds also return `409 INSUFFICIENT_BALANCE` at the API boundary.
