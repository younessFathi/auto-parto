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

## OTP verification

The active email OTP flow binds each challenge to the `LOGIN` purpose and the requested email identity. OTP values are stored only as one-way hashes using the configured HMAC pepper. Challenges have an explicit expiry, can be consumed only once, and are consumed with an atomic state transition. Failed verification attempts are updated atomically and lock the challenge after the bounded failure threshold.

OTP requests and validation requests require a valid email and a six-digit code. Buyer challenge issuance is keyed by the canonical email identity; a phone number supplied with the request cannot redirect or replace another buyer's challenge. Challenge fields, hashes, and codes are excluded from API responses and are not written to application logs. The existing PostgreSQL audit triggers record security-relevant challenge changes without exposing the OTP value.

Invalid, expired, consumed, and locked OTP challenges use the same public `400 OTP_INVALID` response. Request-level rate limiting remains a separate `429 RATE_LIMITED` response. State-specific details remain internal to the persistence and verification path.

Authentication endpoints are rate limited through Redis. A missing or unavailable Redis rate-limit dependency rejects the authentication request instead of allowing an unbounded fallback. Redis-backed counters remain bounded by their configured window and request limit.
