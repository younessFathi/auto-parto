# F04 Money and Wallets

Represent money safely and make every wallet mutation atomic, idempotent, and auditable.

- **Status:** Done

- [AP-0010](../tickets/AP-0010-money-minor-units.md): Standardize all monetary values as integer minor units.
- [AP-0011](../tickets/AP-0011-wallet-ledger.md): Implement atomic wallet balances and immutable ledger entries.

## Completion Tracking

- AP-0010 and AP-0011 are implemented and verified on the PostgreSQL persistence boundary.
- Money uses exact signed 64-bit minor-unit arithmetic with currency-aware scales and overflow detection.
- Wallet mutations lock the owner/currency wallet, append an immutable audited ledger entry, update the balance, and complete the idempotency record in one transaction.
- Replay, conflicting key reuse, concurrent withdrawals, insufficient funds, expired-key cleanup, unsupported currencies, and missing owners are covered by focused tests.
- The public contract and seven-day idempotency retention policy are documented in [`docs/money.md`](../../docs/money.md) and [`docs/wallet-ledger.md`](../../docs/wallet-ledger.md).
