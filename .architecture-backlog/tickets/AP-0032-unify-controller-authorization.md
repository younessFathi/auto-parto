# AP-0032 Unify Controller Authorization Checks

- **Status:** Done
- **Type:** Technical Enhancement
- **Feature:** [Identity and Security](../features/F02-identity-and-security.md)
- **Linked Tickets:** [AP-0004](AP-0004-canonical-authentication.md), [AP-0005](AP-0005-prevent-identity-impersonation.md), [AP-0031](AP-0031-security-authorization-integration-tests.md)
- **Source:** `AGENTS.md` security rules; existing `CanonicalSecurity` and reactive method-security implementation

## Description

Standardize authorization checks across all controllers so role authorization, canonical-principal validation, and resource ownership use one predictable pattern. Controllers currently mix imperative `CanonicalSecurity.requireRole(...)` calls, `@PreAuthorize` annotations, and request-specific ownership checks. This makes authorization behavior harder to review and can lead to inconsistent 401/403 handling or duplicated checks.

Use `@PreAuthorize` for static role requirements, and use shared `CanonicalSecurity` methods only when a controller must validate the canonical principal, derive its durable ID, or enforce ownership. Document and test the boundary between these checks.

## Implementation Tracking

Started on `feat/ap-0032-unify-controller-authorization` from the latest `origin/main`. The implementation will standardize static role checks with `@PreAuthorize`, retain `CanonicalSecurity` for canonical-principal and ownership validation, and add controller/security coverage for the resulting 401/403 behavior.

The implementation adds explicit method-security rules to protected controller methods, removes controller-specific role validation duplication in the profile controllers, preserves canonical ownership and role guards for direct controller invocation, and documents the convention in `docs/authorization-conventions.md`.

Review follow-up adds reactive method-security coverage to the profile controller slice tests. Buyer and seller profile updates now prove missing authentication returns `401`, an authenticated wrong role returns `403`, and neither rejected request invokes its service; the test slice enables the same reactive method-security mechanism used by production.

## Acceptance Criteria

1. Every protected controller method has an explicit, reviewable authorization decision using `@PreAuthorize`, a documented public-endpoint rule, or a shared `CanonicalSecurity` ownership/principal check.
2. Static role requirements use one consistent `@PreAuthorize` expression convention, including the project’s `ROLE_` mapping semantics.
3. Canonical identity extraction always validates `CanonicalPrincipal` and derives actor/resource-owner IDs from the authenticated principal rather than request-controlled identity fields.
4. Ownership checks remain separate from role checks and use one shared implementation for matching owner, cross-owner, missing-owner, and administrator cases.
5. Authorization failures have consistent behavior: unauthenticated or noncanonical authentication returns `401`, an authenticated principal with the wrong role or owner returns `403`, and the existing safe error contract is preserved.
6. Redundant role checks are removed where method security already enforces the same static role, unless the remaining check is required to obtain or validate the canonical principal and is documented.
7. The authorization convention is documented for future controllers, including when to use `@PreAuthorize`, `requirePrincipal`, `requireRole`, `requireOwner`, and `requireClaimedOwner`.

## Test Scenarios

1. **Given** a protected controller method with a static role requirement, **when** a user with the required role calls it, **then** the request succeeds and the service receives the canonical principal identity.
2. **Given** the same method, **when** an authenticated user has a different supported role, **then** the response is `403` and the service is not called.
3. **Given** a protected method, **when** authentication is missing, malformed, expired, or noncanonical, **then** the response is `401` and the controller is not called.
4. **Given** an ownership-sensitive method, **when** the authenticated principal owns the resource, **then** the request succeeds; **when** another principal or a mismatched claimed ID is supplied, **then** the response is `403`.
5. **Given** an administrator-only method, **when** an administrator calls it, **then** it succeeds according to the documented policy; **when** a nonadministrator calls it, **then** it returns `403`.
6. **Given** every protected controller, **when** the security integration suite runs, **then** its authorization convention and safe error response are covered without weakening existing tests.

## Definition of Done

The complete ticket is implemented; every controller follows the documented authorization convention; every acceptance criterion and test scenario is automated and passing; API/security documentation and architecture-backlog tracking are updated; and the full applicable Maven test and verification suites pass.
