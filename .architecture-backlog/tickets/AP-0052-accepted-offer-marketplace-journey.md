# AP-0052 Complete the Accepted-Offer Marketplace Journey

- **Status:** Blocked
- **Type:** User Story
- **Feature:** [F13 Marketplace Business Integrity](../features/F13-marketplace-business-integrity.md)
- **Linked Tickets:** [AP-0051 Offer Authority](AP-0051-offer-decision-authority.md), [AP-0050 Trusted Funding](AP-0050-trusted-wallet-funding.md), [AP-0053 Preference Enforcement](AP-0053-notification-preference-enforcement.md), [AP-0008 Part Listings](AP-0008-part-listings.md), [AP-0012 Notification Outbox](AP-0012-notification-outbox.md)
- **Source:** `PostgresqlOfferService.updateStatus`, existing listing/wallet/notification boundaries; 2026-11-18 business-integrity assessment

## Description

As a buyer or seller, I want accepting an offer to produce the explicitly agreed marketplace outcome so reservation, account effects and participant updates cannot silently disagree with the accepted decision.

[`PostgresqlOfferService.java:53–69`](../../src/main/java/auto/parto/service/PostgresqlOfferService.java) currently validates a decision and calls the offer repository to update status. That path does not orchestrate listing reservation, wallet movement or notification intent. Current acceptance is therefore an offer status decision, not proof of a completed purchase, reservation or payment. The uncovered work is to select and consistently implement required effects; this ticket does not presume they are all desired.

Exclude checkout/provider selection, payouts/refunds, shipping execution and a new order aggregate. Reference existing boundaries without reopening their Done tickets or redesigning their underlying engines.

## Decisions and Dependencies Before Ready

- AP-0051's owner-approved decision matrix is required for both offer origins.
- Owner must approve an effect matrix per origin: listing reservation/availability, buyer-request disposition and other pending-offer disposition; money explicitly included or excluded; notification recipients/event types explicitly required or excluded. Define what Accepted promises and what remains pending or manual.
- If money effects are approved, depend on AP-0050's approved/completed trust boundary and specify authorized debit/credit parties, amounts/currency and failure semantics without selecting a payment provider. If money is excluded, assert no balance change as an explicit contract.
- If participant notifications are required, depend on AP-0053 preference enforcement and specify which recipient channels/types apply. If notifications are excluded, document that exclusion; acceptance must not imply a delivered message.
- Approve transaction/recovery boundaries, conflict outcomes and the durable correlation/deduplication identity for each selected effect. A status-only policy is valid only if the owner explicitly excludes the other effects and the public journey states that limitation.

## Acceptance Criteria

1. Record the approved effect/authority/dependency matrix before Ready. Implement only required effects; do not turn unspecified reservation, money or notification assumptions into behavior.
2. Acceptance verifies the persisted participants, expected offer/target state and AP-0051 decision rights. Competing accepts/reservations cannot both violate the selected availability policy; stale or conflicting attempts return documented safe outcomes.
3. Required database effects commit consistently within the approved transaction boundary, or have explicit durable pending/recovery state where atomic completion is impossible. Never report all required effects completed when a required effect failed or remains pending; expose only safe business status.
4. Money included by policy uses the approved AP-0050 authority boundary and existing exact/audited wallet operations, with at most one authorized movement per business acceptance. Money excluded by policy never changes a wallet merely because acceptance occurred.
5. Required notification intent correlates with the accepted business event and respects AP-0053 at delivery/retry; delivery failure is not misrepresented as business rollback or success. Excluded notifications are not emitted as an implicit acceptance effect.
6. Duplicate acceptance requests, worker retries, new request keys, crashes and restarts cannot duplicate a required reservation, money effect or notification intent, lose a required durable effect, or produce an inconsistent acceptance outcome. Document recovery and unresolved/pending states rather than relying on in-memory completion.
7. Update functional/API documentation to distinguish accepted, required effects completed/pending and excluded/manual effects. Keep all existing role/ownership and safe error contracts.

## Test Scenarios

1. **Given** each approved offer origin and eligible target, **when** the authorized actor accepts, **then** exactly its required effects occur and every excluded effect remains absent.
2. **Given** two offers competing under the selected availability policy or a stale/forged actor, **when** acceptance races, **then** only policy-permitted outcomes commit without conflicting reservations or unauthorized effects.
3. **Given** money is included and funds/authority are insufficient or unavailable, **when** acceptance is attempted, **then** the approved failure/pending policy holds with no partial false completion; **given** money is excluded, **when** accepted, **then** no wallet mutation occurs.
4. **Given** acceptance/selected effects have already been applied, **when** replayed with the same or different request keys before/after restart or worker retry, **then** no selected effect duplicates and the returned/durable business result remains consistent.
5. **Given** failure at each selected transaction/outbox/effect boundary, **when** processing resumes, **then** recovery completes once or reports the approved pending/failure state without lost intent or inconsistent status.
6. **Given** required notifications and recipient opt-outs or a provider outage, **when** delivery/retry runs, **then** preference policy and durable delivery outcomes remain accurate; excluded notifications are never introduced by recovery.

## Definition of Done

The owner-approved journey and its required/excluded effects are explicit and implemented; selected dependencies are satisfied; every applicable matrix case, criterion and failure/retry/concurrency scenario is automated and passing; recovery and business/API documentation are complete; and all applicable suites pass. Conditional scenarios are resolved against the approved matrix, not silently deferred. No unapproved payment/provider/order/shipping scope is added.


## Owner Policy Gate Resolved (2026-11-18)

The owner explicitly approved the policy recorded in [F13](../features/F13-marketplace-business-integrity.md#approved-owner-policies-2026-11-18). This ticket's implementation has not started in the AP-0050 increment. AP-0051 authority and AP-0053 preference implementation are outstanding dependencies; wallet movement is explicitly excluded.
