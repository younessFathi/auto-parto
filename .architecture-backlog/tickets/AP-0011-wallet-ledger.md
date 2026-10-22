# AP-0011 Implement Atomic Wallet Ledger

- **Status:** Done
- **Type:** User Story
- **Feature:** [Money and Wallets](../features/F04-money-and-wallets.md)
- **Linked Tickets:** [AP-0002](AP-0002-reactive-persistence.md), [AP-0005](AP-0005-prevent-identity-impersonation.md), [AP-0010](AP-0010-money-minor-units.md)
- **Source:** `architecture.md` Money and Wallets

## Description
As a wallet owner, I want atomic and auditable balance changes so retries and concurrency cannot corrupt funds.

## Acceptance Criteria
1. Each owner has at most one wallet per currency.
2. Every mutation atomically changes the balance and creates an immutable ledger entry.
3. Retriable commands require an owner/operation-scoped idempotency key and fingerprint retained for a documented period.
4. Insufficient funds and conflicting key reuse return `409`.

## Test Scenarios
1. **Given** sufficient funds, **when** withdrawal succeeds, **then** balance and ledger commit together.
2. **Given** the same key and request, **when** retried, **then** no duplicate balance change occurs.
3. **Given** concurrent excessive withdrawals, **when** executed, **then** overdraft is prevented.
4. **Given** an expired retention period, **when** idempotency records are cleaned, **then** cleanup follows the documented replay-safety policy.

## Definition of Done
The whole ticket is implemented, every acceptance criterion is satisfied, every scenario is automated and passing, concurrency and ledger reconciliation coverage is complete, and all applicable suites pass.

## Implementation Tracking

- PostgreSQL migration V9 stores the completed wallet response with the owner/operation-scoped idempotency record and removes the old permanent ledger-key uniqueness index so the documented retention policy can be enforced.
- `R2dbcWalletRepository` now validates and fingerprints mutation requests, reserves idempotency keys in the same transaction as the wallet lock, replays completed requests, rejects conflicting reuse, and exposes bounded expired-record cleanup.
- Wallet balance updates, immutable ledger entries, audit records, and idempotency completion remain one transactional unit. Insufficient balance and key conflicts map to the safe `409` error contract.
- PostgreSQL integration coverage exercises replay, conflicting reuse, concurrent withdrawals, expired-record cleanup, owner absence, and required keys. See [wallet-ledger.md](../../docs/wallet-ledger.md) for the contract and seven-day retention policy.
