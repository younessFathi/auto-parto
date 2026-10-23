# AP-0008 Manage Seller Part Listings

- **Status:** Done
- **Type:** User Story
- **Feature:** [Marketplace](../features/F03-marketplace.md)
- **Linked Tickets:** [AP-0002](AP-0002-reactive-persistence.md), [AP-0005](AP-0005-prevent-identity-impersonation.md)
- **Source:** `architecture.md` Aggregate Ownership and Lifecycle

## Description
As a seller, I want to publish and manage inventory listings so that buyers can discover available parts.

## Acceptance Criteria
1. Sellers create listings with validated searchable vehicle and part fields.
2. The authenticated seller owns each listing.
3. Guarded transitions support publish, pause, reserve, and close.
4. Business, delivery, and storage statuses remain separate.

## Test Scenarios
1. **Given** an authenticated seller, **when** valid data is submitted, **then** an owned listing is created.
2. **Given** a non-owner, **when** a transition is requested, **then** `403` is returned.
3. **Given** an invalid transition, **when** requested, **then** `409` is returned.

## Definition of Done
The whole ticket is implemented, every acceptance criterion is satisfied, every scenario is automated and passing, API and persistence documentation are complete, and all applicable suites pass.

## Implementation Tracking

- Started on `feat/ap-0008-part-listings` from the latest `origin/main`.
- Design boundary: seller ownership comes only from the canonical authenticated principal; business lifecycle, delivery readiness, and storage state will be persisted and exposed as separate fields.
- Added PostgreSQL V6 with independent business, delivery, and storage statuses, lifecycle checks, and the seller listing index.
- Added seller create, bounded listing pagination, and guarded lifecycle transition endpoints using canonical principal ownership.
- Added DTOs and API documentation for searchable vehicle fields, monetary values, lifecycle state, and versioned responses.
- Added service, controller/security, PostgreSQL repository, migration, and schema coverage for ownership, validation, status separation, and transitions.
- Verification: focused controller/service/repository/schema tests pass; `./mvnw -B test` passed with 331 tests and one existing skip; `./mvnw -B verify` passed with 115 Failsafe integration tests and one existing skip; JaCoCo checks passed.
