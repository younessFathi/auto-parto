# AP-0050 Require Trusted Wallet Funding Authority

- **Status:** Proposed
- **Type:** Technical Enhancement
- **Feature:** [F13 Marketplace Business Integrity](../features/F13-marketplace-business-integrity.md)
- **Linked Tickets:** [AP-0011 Wallet Ledger](AP-0011-wallet-ledger.md), [AP-0052 Accepted-Offer Journey](AP-0052-accepted-offer-marketplace-journey.md)
- **Source:** `SellerController.addFunds`, `SellerService.addFunds`, `R2dbcWalletRepository.mutate`; 2026-11-18 business-integrity assessment

## Description

Make wallet credits depend on an explicitly approved funding authority and verifiable funding evidence. A seller currently supplies the amount for their own credit: [`SellerController.java:76–85`](../../src/main/java/auto/parto/api/SellerController.java) checks seller ownership and delegates, while [`SellerService.java:59–66`](../../src/main/java/auto/parto/service/SellerService.java) forwards that amount with the constant `seller-wallet-api` reference. [`R2dbcWalletRepository.java:47–67`](../../src/main/java/auto/parto/persistence/R2dbcWalletRepository.java) has no independent funding-evidence check. Ownership and request idempotency alone do not establish why funds may be credited.

Scope is this trust boundary and its business-event replay protection, not a rewrite of ledger arithmetic/persistence. Do not introduce a payment provider, checkout, payout, refund or new settlement integration under this ticket.

## Decisions and Dependencies Before Ready

- Owner must select the allowed funding authority and evidence, its authorized actors, and how amount/currency/beneficiary are verified; do not assume seller self-credit, an admin endpoint or a payment provider is approved.
- Decide whether the current seller credit route is removed, restricted or converted to a non-crediting request. Specify compatibility/error behavior and any explicitly isolated non-live development allowance.
- Define a stable funding-event identity, its retention/reuse policy and safe reconciliation outcome when evidence validation or persistence is unavailable. Client request-key expiry must not allow the same confirmed funding event to create fresh money.
- AP-0011 is a reference; its Done scope remains unchanged. This ticket must preserve its invariants while adding the approved authority/evidence boundary.

## Acceptance Criteria

1. Record owner approval of the authority, evidence, actors, existing-route treatment, event identity/retention and failure policy before Ready or implementation.
2. Every path that can create a positive wallet credit verifies the approved authority and binds verified evidence to beneficiary, exact amount and currency. Untrusted seller input, another owner's identity, arbitrary references and signed-amount/alias-route variations cannot bypass the boundary.
3. Reject missing, forged, mismatched, invalid or previously consumed evidence with safe documented outcomes and no unauthorized balance/ledger change. If authority validation is unavailable, follow the approved non-crediting failure/pending policy.
4. Bind one funding event to at most one authorized credit across concurrent submissions, different client idempotency keys, normal key retention expiry, retries and restart. Conflicting reuse cannot create additional funds or conceal inconsistent evidence.
5. Preserve atomic balance/ledger/audit/idempotency consistency and existing exact currency arithmetic. Keep only the minimal safe authority/evidence correlation needed for audit/recovery; never store credentials or sensitive provider payloads in logs/API responses.
6. Document the resulting API/compatibility contract and operational recovery for an uncertain attempt. No external settlement success is claimed merely because an internal credit exists.

## Test Scenarios

1. **Given** approved authority and valid evidence for an owner/amount/currency, **when** funding is applied, **then** exactly the authorized credit and its audit correlation commit.
2. **Given** an authenticated seller with only an amount/request key, **when** they try any positive-credit path including an inverted-sign or alias variation, **then** no unverified money is created.
3. **Given** forged/missing evidence, wrong owner, altered amount/currency or an unauthorized actor, **when** submitted, **then** safe rejection leaves balances and ledger unchanged.
4. **Given** the same funding event and concurrent or different request keys, **when** attempts repeat before/after request-key expiry or process restart, **then** at most one credit exists and replay/conflict results follow the approved policy.
5. **Given** authority or database failure before/during application, **when** retried or recovered, **then** the attempt is safely rejected/pending or completed once, without a false success or duplicate funding event.

## Definition of Done

The complete approved trust-boundary scope is implemented; every criterion and scenario is automated and passing; audit/recovery, API compatibility and relevant documentation are complete; no provider/checkout scope was silently added; and all applicable test/verification suites pass. No criterion or scenario may be deferred or weakened.
