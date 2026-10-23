# AP-0004 Establish Canonical Authentication

- **Status:** Done
- **Type:** Technical Enhancement
- **Feature:** [Identity and Security](../features/F02-identity-and-security.md)
- **Linked Tickets:** [AP-0001](AP-0001-postgresql-schema.md), [AP-0002](AP-0002-reactive-persistence.md)
- **Source:** `architecture.md` Authentication and Authorization

## Description
Enable one documented authentication model per profile and produce one canonical principal with identity and role.

## Current Implementation Slice
The active profile uses email OTP to issue a signed application JWT. JWT validation creates `CanonicalPrincipal`, carrying the stable identity, email, and role used by authorization and ownership checks. Google and Clerk flows are disabled until their complete provider configuration and verification tests are implemented.

## Acceptance Criteria
1. Each profile enables only its documented authentication mechanism.
2. Valid credentials create one canonical principal.
3. Invalid, expired, or incorrectly issued credentials return `401`.
4. Incomplete authentication integrations remain disabled.

## Test Scenarios
1. **Given** valid configured credentials, **when** authentication runs, **then** a canonical principal is created.
2. **Given** invalid credentials, **when** a protected endpoint is called, **then** `401` is returned.
3. **Given** an unconfigured provider, **when** its flow is requested, **then** it is unavailable.

## Definition of Done
The whole ticket is implemented, every acceptance criterion is satisfied, every scenario is automated and passing, security configuration and documentation are complete, and all applicable suites pass.
