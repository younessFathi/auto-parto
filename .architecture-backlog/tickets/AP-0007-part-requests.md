# AP-0007 Manage Buyer Part Requests

- **Status:** Done
- **Type:** User Story
- **Feature:** [Marketplace](../features/F03-marketplace.md)
- **Linked Tickets:** [AP-0002](AP-0002-reactive-persistence.md), [AP-0005](AP-0005-prevent-identity-impersonation.md)
- **Source:** `architecture.md` Aggregate Ownership and Lifecycle

## Description
As a buyer, I want to publish and manage part requests so that sellers can find and answer my needs.

## Acceptance Criteria
1. Buyers create requests with validated searchable vehicle and part fields.
2. The authenticated buyer owns each created request.
3. Only the owner can cancel or close a request.
4. Invalid state transitions return `409`.

## Test Scenarios
1. **Given** an authenticated buyer, **when** valid data is submitted, **then** an owned request is created.
2. **Given** a different buyer, **when** cancellation is attempted, **then** `403` is returned.
3. **Given** a closed request, **when** another invalid transition is attempted, **then** `409` is returned.

## Definition of Done
The whole ticket is implemented, every acceptance criterion is satisfied, every scenario is automated and passing, API and persistence documentation are complete, and all applicable suites pass.

## Implementation Tracking

- Added buyer-owned request creation, listing, close, and cancel endpoints backed by PostgreSQL/R2DBC.
- Enforced canonical buyer authentication, owner checks, public UUID responses, validation, and `OPEN`-only state transitions.
- Added unit, controller/security, and PostgreSQL Testcontainers coverage.
- Added the API contract documentation in `docs/api-part-requests.md`.
- Replaced offset pagination with bounded cursor pagination (`limit` 1-100, default 50), stable `created_at DESC, public_id DESC` ordering, and keyset SQL predicates.
- Added `nextCursor` response handling and Testcontainers coverage for page boundaries, tied timestamps, and buyer isolation.
