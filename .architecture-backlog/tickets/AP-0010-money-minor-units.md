# AP-0010 Standardize Money as Minor Units

- **Status:** Done
- **Type:** Technical Enhancement
- **Feature:** [Money and Wallets](../features/F04-money-and-wallets.md)
- **Linked Tickets:** [AP-0001](AP-0001-postgresql-schema.md)
- **Source:** `architecture.md` Money and Wallets

## Description
Represent every balance and price as integer minor units plus ISO currency without floating-point conversion.

## Acceptance Criteria
1. All monetary values use signed 64-bit minor units and currency.
2. Supported currencies define valid scales, including zero- and three-decimal currencies.
3. Excess precision, unsupported currencies, invalid signs, and cross-currency arithmetic are rejected.
4. Monetary arithmetic detects overflow.

## Test Scenarios
1. **Given** a valid currency amount, **when** parsed, **then** it maps exactly to minor units.
2. **Given** excess precision or mismatched currencies, **when** submitted, **then** validation rejects it.
3. **Given** arithmetic overflow, **when** calculated, **then** no value is persisted.

## Implementation Tracking

- Added the `Money` value type with currency-aware parsing, exact decimal conversion, same-currency arithmetic, signed 64-bit overflow checks, and non-negative validation.
- Added minor-unit scales for supported zero-, two-, and three-decimal currencies.
- Converted legacy wallet and piece-offer models/DTOs to minor-unit fields and removed floating-point prices from notification request DTOs.
- Validated supported currency codes at PostgreSQL wallet, listing, and offer repository boundaries.
- Added unit coverage for precision, currency mismatch, negative values, and overflow, plus PostgreSQL/Testcontainers coverage for unsupported currency rejection.
- Addressed review findings by rejecting the `Long.MIN_VALUE` debit edge case, restoring the schema test to the four migrations that exist, removing unused notification price fields without currency context, and adding PostgreSQL/Testcontainers coverage for the debit guard.
- Added [`docs/money.md`](../../docs/money.md) describing the representation and compatibility boundary.

## Definition of Done
The whole ticket is implemented, every acceptance criterion is satisfied, every scenario is automated and passing, API and schema documentation are complete, and all applicable suites pass.
