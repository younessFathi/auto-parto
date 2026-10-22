# AP-0009 Create Offers and Marketplace Matching

- **Status:** Done
- **Type:** User Story
- **Feature:** [Marketplace](../features/F03-marketplace.md)
- **Linked Tickets:** [AP-0007](AP-0007-part-requests.md), [AP-0008](AP-0008-part-listings.md)
- **Source:** `architecture.md` Suggested Relational Boundaries

## Description
As a marketplace participant, I want eligible requests, listings, and offers connected so that negotiations are relevant and bounded.

## Acceptance Criteria
1. Offers identify buyer, seller, and an eligible request or listing.
2. Participant identity and ownership are verified before mutation.
3. Offer transitions are guarded and concurrency-safe.
4. Matching uses indexed attributes with a bounded result and fan-out limit.

## Test Scenarios
1. **Given** eligible participants and a target, **when** an offer is created, **then** relationships are persisted.
2. **Given** an unrelated user, **when** an offer is changed, **then** `403` is returned.
3. **Given** concurrent incompatible updates, **when** both run, **then** only one valid transition succeeds.
4. **Given** indexed matching data, **when** a request is matched, **then** results and notification fan-out remain within configured bounds.

## Definition of Done
The whole ticket is implemented, every acceptance criterion is satisfied, every scenario is automated and passing, query indexes and API documentation are complete, and all applicable suites pass.

## Implementation Tracking

- The existing PostgreSQL offer boundary persists one request or listing target, derives the counterparty owner from that target, and uses optimistic versions for status updates.
- Offer status transitions are now guarded by actor ownership and an explicit pending-state transition policy.
- `GET /api/v1/part-requests/{publicId}/matches?limit=20` returns at most 50 published listing matches for the authenticated owner of an open request.
- Matching accepts a finite indexed filter contract: unfiltered, condition-only, category-plus-condition, complete vehicle tuple, or category-plus-condition with a complete vehicle tuple. Partial vehicle, category-only, and mixed vehicle groups are rejected with 400. Migration V8 provides a partial index for each supported shape, and Testcontainers plans cover each shape without disabling sequential scans.
