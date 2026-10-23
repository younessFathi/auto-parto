# AP-0006 Provide Secure OTP Verification

- **Status:** Done
- **Type:** User Story
- **Feature:** [Identity and Security](../features/F02-identity-and-security.md)
- **Linked Tickets:** [AP-0004](AP-0004-canonical-authentication.md)
- **Source:** `architecture.md` Authentication and PostgreSQL Design

## Description
As a user, I want expiring single-use OTP verification so that account access is secure and retry-safe.

## Acceptance Criteria
1. OTP challenges are bound to the `LOGIN` purpose and canonical email identity, stored as one-way hashes, explicitly expiring, single-use, and attempt-bounded.
2. Successful validation consumes the challenge atomically on the PostgreSQL identity path.
3. Expired, incorrect, locked, or consumed challenges fail safely without revealing which challenge state caused the failure.
4. OTP values, hashes, and challenge metadata never appear in logs or API responses.
5. Authentication requests require a valid email and six-digit OTP format, buyer challenge issuance is keyed by canonical email, and authentication rate limiting fails closed when Redis is unavailable.
6. Existing PostgreSQL audit behavior records challenge changes without exposing OTP values.

## Test Scenarios
1. **Given** a valid challenge, **when** the correct OTP is submitted concurrently, **then** exactly one request succeeds.
2. **Given** an expired, identity-mismatched, consumed, or purpose-mismatched challenge, **when** it is submitted, **then** verification fails safely.
3. **Given** repeated concurrent failures, **when** the limit is reached, **then** further attempts are blocked and the failure counter remains capped at the configured maximum.
4. **Given** a PostgreSQL challenge, **when** it is inspected after creation and consumption, **then** the plaintext OTP is absent and the challenge is marked consumed atomically.
5. **Given** Redis is unavailable, **when** an authentication request is made, **then** rate limiting rejects it rather than failing open.
6. **Given** a phone number paired with another email, **when** a buyer requests an OTP, **then** only the canonical email challenge is created or replaced and no victim challenge is changed.
7. **Given** any invalid OTP challenge state, **when** it is submitted through HTTP, **then** the public response is the same generic invalid-OTP contract.

## Implementation tracking

- PostgreSQL identity OTPs already use the HMAC pepper, `LOGIN` purpose, email identity, explicit expiry, atomic consumption, a permanent five-attempt cap on both buyer and seller paths, and audit-trigger coverage.
- PostgreSQL OTP storage now stores per-challenge PBKDF2 hashes, records purpose and identity metadata, sets explicit expiry, atomically consumes a matching challenge, and atomically records failed attempts and lockout.
- PostgreSQL consumption and failed-attempt updates include the exact verified hash and all eligibility fields in their compare-and-set predicates; failed attempts are capped atomically at five.
- OTP request validation requires an email and a six-digit code. OTP and challenge internals are ignored at the API boundary and are not logged.
- Buyer issuance is email-keyed so a phone/email mismatch cannot replace or deliver a victim's challenge.
- All externally visible OTP challenge failures use one generic `400 OTP_INVALID` response; request-level Redis rate limiting remains a separate `429 RATE_LIMITED` response.
- Authentication rate limiting is five requests per minute through Redis and fails closed when Redis is missing or unavailable.
- Focused unit, controller, and PostgreSQL Testcontainers coverage covers hashing, response privacy, validation, rate-limit failure behavior, concurrent single-use consumption, and concurrent lockout updates.

## Definition of Done
The whole ticket is implemented, every acceptance criterion is satisfied, every scenario is automated and passing, security and concurrency coverage is complete, and all applicable suites pass.
