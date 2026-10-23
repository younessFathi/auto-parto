# AP-0005 Prevent Client-Supplied Identity Impersonation

- **Status:** Done
- **Type:** Bug Fix
- **Feature:** [Identity and Security](../features/F02-identity-and-security.md)
- **Linked Tickets:** [AP-0004](AP-0004-canonical-authentication.md)
- **Source:** `architecture.md` Request Flow and Authentication and Authorization

## Description
Derive every sensitive actor from the principal and enforce role and resource ownership independently.

## Implemented Slice
Sensitive buyer, seller, piece, device-token, notification-statistics, and user-specific SSE operations now derive actor identity from `CanonicalPrincipal`. Supplied actor IDs are accepted only when they match the authenticated principal, role and ownership checks are performed independently, and rejected ownership or role attempts return `403` through the shared safe error contract. Focused controller tests cover owner success, wrong-role rejection, cross-user rejection, canonical authentication failures, and the notification actor-ID paths.

Generic FCM/SSE dispatch and seller-response delivery are restricted to canonical admin/internal callers. Seller self-service response delivery requires a future persisted search/response relationship model before it can safely be exposed.

## Acceptance Criteria
1. Sensitive operations never trust a client-supplied actor ID.
2. Role and ownership checks are separate decisions.
3. Owners with the required role can act on their resources.
4. Cross-user access returns `403` without sensitive details.

## Test Scenarios
1. **Given** another user's resource ID, **when** a mutation is attempted, **then** `403` is returned.
2. **Given** the correct role without ownership, **when** access is attempted, **then** it is denied.
3. **Given** the owner with the required role, **when** a valid action is requested, **then** it succeeds.

## Definition of Done
The whole ticket is implemented, every acceptance criterion is satisfied, every scenario is automated and passing, controller and security coverage is complete, and all applicable suites pass.
