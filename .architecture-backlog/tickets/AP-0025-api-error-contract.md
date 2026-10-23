# AP-0025 Standardize API Error Contract

- **Status:** In Progress
- **Type:** Technical Enhancement
- **Feature:** [Platform and Data](../features/F01-platform-and-data.md)
- **Linked Tickets:** [AP-0004](AP-0004-canonical-authentication.md), [AP-0005](AP-0005-prevent-identity-impersonation.md)
- **Source:** `architecture.md` Error Handling

## Description
Provide one safe error-response contract and shared mapping for validation, authentication, authorization, missing data, conflicts, and unexpected failures.

## Acceptance Criteria
1. Shared handling maps validation to `400`, authentication to `401`, authorization to `403`, missing resources to `404`, and state/idempotency conflicts to `409`.
2. Accepted asynchronous work uses `202` where applicable.
3. Responses never expose internal exceptions, credentials, tokens, OTPs, or provider payloads.
4. Unexpected failures return a stable safe error code and correlation identifier.

## Test Scenarios
1. **Given** each domain failure category, **when** an endpoint fails, **then** the documented status and response shape are returned.
2. **Given** an internal dependency exception, **when** mapped, **then** sensitive details are absent.
3. **Given** an unexpected failure, **when** returned, **then** support can correlate it without exposing internals.

## Definition of Done
The whole ticket is implemented, every acceptance criterion is satisfied, every scenario is automated and passing, public API documentation is complete, and all applicable suites pass.

## Implementation Tracking

- Added a stable `code` field and safe shared mappings for validation, authentication, authorization, missing resources, conflicts, file failures, OTP failures, and unexpected failures.
- Added `ConflictException` for state and idempotency conflict handling.
- Added shared reactive error writing for pre-controller JWT and rate-limit failures, including correlation IDs.
- Mapped persistence duplicate-key and integrity failures to the 409 conflict contract.
- Added unit, WebFlux contract, and PostgreSQL/Redis Testcontainers coverage.
- Updated PostgreSQL migration integration assertions to match the three migrations present on main.
- Added [`docs/api-error-contract.md`](../../docs/api-error-contract.md) with the response shape and status/code mapping.
- Remaining work: remove raw internal error text from all endpoint responses and add endpoint-level regression coverage before marking the ticket Done.
