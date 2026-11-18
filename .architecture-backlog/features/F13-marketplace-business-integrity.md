# F13 Marketplace Business Integrity

Close four uncovered business-policy and orchestration gaps: trusted wallet credit, offer decision authority, accepted-offer effects, and notification preference enforcement.

**Status:** In Progress

The owner initially authorized feature creation on 2026-11-18 and subsequently explicitly approved implementation and the policy bundle below. Tickets advance separately after their dependencies and verification gates are satisfied. Existing Done tickets remain unchanged; they are references, not work to repeat.

## Tickets

- [AP-0050 Trusted wallet funding](../tickets/AP-0050-trusted-wallet-funding.md): Require an approved funding authority/evidence for credits, instead of treating seller ownership and a request reference as funding proof. **In Progress**
- [AP-0051 Offer decision authority](../tickets/AP-0051-offer-decision-authority.md): Define and enforce decision rights for buyer-created and seller-created offers, including whether creators may accept their own offers. **In Progress (implemented; final gates pending)**
- [AP-0052 Accepted-offer marketplace journey](../tickets/AP-0052-accepted-offer-marketplace-journey.md): Decide which reservation, money and notification effects acceptance requires, then make that exact journey recoverable and consistent. **Blocked (AP-0051/AP-0053 implementation dependencies)**
- [AP-0053 Notification preference enforcement](../tickets/AP-0053-notification-preference-enforcement.md): Make recipient channel/type/default policy consistent and effective at actual delivery, including queued/retried sends. **In Progress (implemented; final gates pending)**

## Approved Scope and Dependency Gates

AP-0050, AP-0051 and AP-0053 have approved policies and can be delivered in the selected order: trusted funding, offer authority, then preferences. AP-0052 is blocked on AP-0051 authority and AP-0053 preference implementation; its selected policy excludes wallet movement, so no payment provider or money movement is implied. The complete approved bundle is recorded below. Each ticket requires its own focused tests, documentation and independent review before completion.

## Boundaries and completion

This feature does not select a payment/provider integration, create checkout or an order aggregate, add payouts/refunds/shipping, or broaden infrastructure/deployment work. Any such need requires a separately approved scope change. Use existing persistence/delivery mechanisms where appropriate; do not reopen their Done tickets as a recap.

The feature is complete only when each approved policy is recorded, its resulting ticket criteria/scenarios are satisfied and verified, and the selected cross-ticket journey passes duplicate, concurrent, dependency-failure and restart/recovery cases without contradictory business results. Until then the feature remains In Progress; policy approval does not imply completed runtime verification or provider changes.


## Approved Owner Policies (2026-11-18)

The owner explicitly selected all recommended answers: AP-0050 administrator-only confirmed manual funding with a globally unique recorded reference; seller self-credit is blocked, including signed-debit bypass. AP-0051 offer recipient accepts/rejects for each origin; either participant may cancel pending offers, with no administrator override. AP-0052 acceptance atomically reserves its listing or closes its request, rejects pending competitors for the same target and records two participant notification intents; automatic wallet movement is excluded. AP-0053 uses current channel AND type preferences with existing DTO defaults: email/push and known transactional types on, SMS/marketing off. Push applies to SSE and FCM; explicit enum/legacy mappings must use known categories, unknown identifiers are rejected, lookup errors remain pending for bounded retry rather than acknowledged delivery. Authentication OTP is outside optional preferences and heartbeats are not notification content. These policies authorize implementation, not real administrator provisioning, a new provider, checkout or deployment. AP-0050 is being implemented first; AP-0051, AP-0053 and then AP-0052 remain separate increments.
