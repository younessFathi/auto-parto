# Controller Authorization Conventions

Controller authorization has two separate responsibilities:

- `@PreAuthorize` enforces a static access rule such as `hasRole('BUYER')`, `hasRole('SELLER')`, `hasRole('ADMIN')`, or `isAuthenticated()` before the controller method runs.
- `CanonicalSecurity` validates the canonical principal and performs request-specific ownership checks. It derives durable actor IDs from the authenticated principal and never trusts a caller-supplied owner ID.

Use `@PreAuthorize` for a role or authentication requirement that is constant for the endpoint. Use `CanonicalSecurity.requirePrincipal(authentication)` when the method needs the canonical ID or email. Use `requireOwner(authentication, requestedId, null)` when the path or request identifies a resource that must belong to the principal. Use `requireClaimedOwner(authentication, claimedId, null)` when a request may omit its owner field but any supplied value must match the principal. Pass a role to the ownership helpers only when method security cannot express the rule at the boundary.

The standard role expression uses the role name without the `ROLE_` prefix because Spring Security's `hasRole` adds that prefix. For example, a buyer endpoint uses `@PreAuthorize("hasRole('BUYER')")` and a seller endpoint uses `@PreAuthorize("hasRole('SELLER')")`.

Keep the canonical helper at the controller boundary when the method also needs the principal or when direct controller invocation tests bypass method-security proxies. The annotation remains the production method-security rule; the helper preserves the same decision for direct invocations and validates the canonical principal. Keep ownership checks after role authorization so wrong-role requests return `403` before service work begins. Missing, malformed, expired, or noncanonical authentication remains a `401` through the authentication filter or `requirePrincipal`; an authenticated principal with the wrong role or owner remains a `403`.
