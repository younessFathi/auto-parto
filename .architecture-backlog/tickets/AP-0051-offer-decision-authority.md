# AP-0051 Define and Enforce Offer Decision Authority

- **Status:** Proposed
- **Type:** User Story
- **Feature:** [F13 Marketplace Business Integrity](../features/F13-marketplace-business-integrity.md)
- **Linked Tickets:** [AP-0009 Offers and Matching](AP-0009-offers-and-matching.md), [AP-0052 Accepted-Offer Journey](AP-0052-accepted-offer-marketplace-journey.md)
- **Source:** `OfferController`, `PostgresqlOfferService.allowed`; 2026-11-18 business-integrity assessment

## Description

As a marketplace participant, I want explicitly defined decision rights for both offer origins so acceptance/rejection represents the intended party's business consent.

The current API creates a seller offer for a buyer request and a buyer offer for a seller listing ([`OfferController.java:26–40`](../../src/main/java/auto/parto/api/OfferController.java)). [`PostgresqlOfferService.java:98–106`](../../src/main/java/auto/parto/service/PostgresqlOfferService.java) permits only the seller participant to accept/reject any pending offer, including an offer that seller created; either participant can cancel. Record this as observed behavior, not a claim that recipient-only or seller-only decisions are already approved.

Scope is the policy matrix and enforcement of existing offer decisions. Counteroffers, negotiation history, checkout, payment and a new order aggregate are excluded.

## Decisions and Dependencies Before Ready

- Owner must approve who may accept, reject and cancel for each origin: seller-created request offer and buyer-created listing offer. Explicitly decide whether creators may decide their own offers and whether recipient consent is required.
- Define allowed source/target statuses, handling of stale/repeated decisions and compatibility with clients relying on the current seller-only rule.
- AP-0009 remains a reference, not reopened work. AP-0052 consumes this approved authority policy and must not implement a conflicting matrix.

## Acceptance Criteria

1. Record an owner-approved actor/origin/transition matrix and compatibility/error policy before Ready; include creator, counterpart, unrelated actor and administrator behavior without assuming an admin bypass.
2. Derive actors/participants and offer origin from canonical authentication and persisted target relationships; client-supplied participant/role fields cannot grant decision rights.
3. Enforce the exact matrix on all decision routes and aliases while preserving participant read restrictions. Neither origin may accidentally inherit permission intended only for the other origin.
4. Guard version/status changes under concurrency. Repeated, stale and conflicting decisions return the approved stable outcome without a second transition or an impossible combination of terminal states.
5. Document both user journeys and authorization/error outcomes. This ticket does not claim acceptance performs money, reservation or notification effects; those decisions belong to AP-0052.

## Test Scenarios

1. **Given** each offer origin and its approved decision actor, **when** every permitted accept/reject/cancel transition is requested, **then** it succeeds exactly as the matrix defines.
2. **Given** the creator/counterpart cases for each origin, **when** an action the matrix forbids is attempted, **then** it is denied without changing the offer; any explicitly permitted self-decision is tested as intentional policy.
3. **Given** an unrelated user, forged participant claim or administrator without a matrix permission, **when** deciding or reading an offer, **then** existing ownership boundaries remain enforced.
4. **Given** simultaneous opposite decisions or an outdated expected version, **when** requests race, **then** only the allowed transition wins and losing/repeated calls follow the documented result without contradictory states.
5. **Given** database failure or restart around a decision, **when** retried, **then** persisted state and returned outcome agree and no extra decision is applied.

## Definition of Done

The approved policy matrix is implemented for both origins; every criterion/scenario is automated and passing; compatibility, authorization and user/API documentation are updated; and all applicable suites pass. No criterion or scenario may be deferred or weakened.
