# AP-0031 Verify Security Authorization End to End

- **Status:** Done
- **Type:** Technical Enhancement
- **Feature:** [Identity and Security](../features/F02-identity-and-security.md)
- **Linked Tickets:** [AP-0004](AP-0004-canonical-authentication.md), [AP-0005](AP-0005-prevent-identity-impersonation.md)
- **Source:** AP-0004 implementation-review follow-up

## Description

Add application-security-chain integration tests for canonical JWT authentication, role authorization, and ownership enforcement on sensitive routes. Unit tests already cover JWT parsing and filter behavior; this ticket verifies that Spring WebFlux security, reactive method security, controllers, and ownership checks operate together.

## Implementation Slice

Add Docker-backed WebFlux security integration coverage using generated test signing keys. Exercise the real JWT filter, security chain, reactive method security, sensitive FCM role boundaries, canonical ownership checks, and notification-preference migration. Map reactive method-security denials to the documented `403 Forbidden` response.

## Acceptance Criteria

1. Protected routes reject missing, malformed, expired, incorrectly signed, and correctly signed JWTs with missing, blank, unsupported, or multiple canonical identity/role claims using `401`.
2. Each sensitive FCM route accepts only its documented canonical role and returns `403` for every other supported role.
3. Ownership-sensitive notification-preference operations use `CanonicalPrincipal.id` when it differs from the principal email, including migration of a legacy email-keyed preference record.
4. Tests exercise the real `SecurityWebFilterChain` and `@EnableReactiveMethodSecurity`, rather than mocking the filter or controller authentication argument.
5. Test keys are generated during test setup or sourced from test-only environment configuration; no key material is committed as a literal.

## Test Scenarios

1. **Given** each invalid canonical-token variant, **when** a protected endpoint is called through the application security chain, **then** it returns `401` and the controller is not invoked.
2. **Given** BUYER, SELLER, and ADMIN JWTs, **when** each FCM route is called, **then** only the documented role succeeds and all other roles receive `403`.
3. **Given** a canonical principal whose ID differs from its email and a legacy email-keyed notification preference, **when** the preference endpoint is called, **then** the returned and persisted record uses the canonical ID without losing the preference values.
4. **Given** a request authenticated as a non-canonical Spring principal, **when** it reaches a canonical-only ownership endpoint, **then** it returns `401`.

## Definition of Done

The complete ticket is implemented; every acceptance criterion and test scenario is automated and passing; Docker-backed dependencies are used where required; no secrets or test key literals are committed; and the full applicable Maven test and verification suites pass.

## Implementation Result

- Added `SecurityAuthorizationIT` with generated HMAC test keys and Docker-backed PostgreSQL/Redis dependencies.
- Covered all missing/blank canonical identity and role claim variants, FCM accepted-role and denial boundaries, registration ownership, statistics owner/admin access, canonical notification-preference migration, and the real WebFlux security chain.
- Added explicit `AccessDeniedException` handling so reactive method-security denials return `403 Forbidden`.
- Added direct controller-path coverage for rejection of a noncanonical principal, while retaining the real JWT security-chain tests for token authentication.
- Full Maven unit and integration verification passed with JaCoCo checks.
