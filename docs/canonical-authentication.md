# Canonical Authentication

The current authentication profile uses email OTP as the only enabled credential flow:

```text
email OTP
    -> OTP validation
    -> signed application JWT
    -> CanonicalPrincipal
    -> role authorization and ownership checks
```

The JWT contains the canonical identity ID, email, and one `ROLE_*` authority. After signature and expiration validation, the application creates `CanonicalPrincipal`, which exposes:

- `id`: the durable application identity used by ownership checks. Notification preferences now use this ID; a read of an existing email-keyed preference migrates that record to the canonical ID;
- `email`: the authenticated email used by existing service lookups;
- `role`: the canonical application role;
- Spring Security authorities: the corresponding `ROLE_BUYER`, `ROLE_SELLER`, or `ROLE_ADMIN` authority.

Google OAuth and Clerk authentication are currently disabled because their issuer, key, claims, and persistence integrations are not complete. Their endpoints and filters are not part of the active application. They must not be enabled by adding partial configuration; a future provider must define its issuer/key configuration, claim-to-principal mapping, account lookup, role mapping, invalid-token behavior, and integration tests before becoming a profile-specific authentication model.

Invalid, expired, malformed, or unsigned tokens are rejected with `401 Unauthorized`. Protected endpoints require the canonical principal, and method-level role checks use its authorities. Authentication and authorization remain separate: role access does not replace resource ownership checks.

Sensitive resource operations derive the acting buyer or seller ID from `CanonicalPrincipal.id`. A client-supplied actor ID may be omitted, but when supplied it must match the canonical ID; mismatches and cross-user mutations return `403 Forbidden`.

Generic FCM and SSE dispatch endpoints, including broadcast and seller-response delivery, are admin/internal operations. They do not accept ordinary buyer or seller callers. Seller self-service response delivery remains unavailable until persisted search/response relationships can be loaded and checked against the authenticated seller and recipient buyer.

The JWT signing secret is required through `JWT_SECRET` and must be a Base64-encoded key of at least 256 bits. It must be supplied through the environment or an approved secret manager and must not be committed to the repository.
