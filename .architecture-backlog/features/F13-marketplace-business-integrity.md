# F13 Marketplace Business Integrity

Close four uncovered business-policy and orchestration gaps: trusted wallet credit, offer decision authority, accepted-offer effects, and notification preference enforcement.

**Status:** Proposed

The owner authorized this feature/ticket creation on 2026-11-18, not the business policies or implementation. Every ticket stays Proposed until its decision gates have explicit owner answers and dependencies are satisfied. Existing Done tickets remain unchanged; they are references, not work to repeat.

## Tickets

- [AP-0050 Trusted wallet funding](../tickets/AP-0050-trusted-wallet-funding.md): Require an approved funding authority/evidence for credits, instead of treating seller ownership and a request reference as funding proof. **Proposed**
- [AP-0051 Offer decision authority](../tickets/AP-0051-offer-decision-authority.md): Define and enforce decision rights for buyer-created and seller-created offers, including whether creators may accept their own offers. **Proposed**
- [AP-0052 Accepted-offer marketplace journey](../tickets/AP-0052-accepted-offer-marketplace-journey.md): Decide which reservation, money and notification effects acceptance requires, then make that exact journey recoverable and consistent. **Proposed**
- [AP-0053 Notification preference enforcement](../tickets/AP-0053-notification-preference-enforcement.md): Make recipient channel/type/default policy consistent and effective at actual delivery, including queued/retried sends. **Proposed**

## Scope and decisions before Ready

1. Approve AP-0050's funding authority, evidence/replay identity and treatment of the existing seller credit route.
2. Approve AP-0051's actor/transition matrix separately for both offer origins; do not assume the current seller-only rule is the desired policy.
3. Approve AP-0053's event/channel/default/error/timing policy, including any explicitly justified mandatory-message exception.
4. Approve AP-0052's effect matrix: listing reservation and request disposition, money explicitly included or excluded, and recipients/events explicitly required or excluded. If money is included, AP-0050 is a dependency; preference-respecting notifications depend on AP-0053. AP-0051's accepted decision policy is required in every case.

AP-0050, AP-0051 and AP-0053 can be refined independently. AP-0052 follows its selected prerequisites and must not silently choose a payment flow or infer that Accepted means payment/reservation/delivery already completed.

## Boundaries and completion

This feature does not select a payment/provider integration, create checkout or an order aggregate, add payouts/refunds/shipping, or broaden infrastructure/deployment work. Any such need requires a separately approved scope change. Use existing persistence/delivery mechanisms where appropriate; do not reopen their Done tickets as a recap.

The feature is complete only when each approved policy is recorded, its resulting ticket criteria/scenarios are satisfied and verified, and the selected cross-ticket journey passes duplicate, concurrent, dependency-failure and restart/recovery cases without contradictory business results. Until then all four concerns remain proposed work; no runtime or provider changes are delivered by this document.
