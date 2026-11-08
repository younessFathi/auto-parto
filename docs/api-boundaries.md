# API Boundary and Route Catalog

The public business API uses versioned `/api/v1` routes, with the documented authentication,
key-discovery, and compatibility exceptions below. Controllers accept and return DTOs,
derive ownership from the authenticated canonical principal, and pass persistence work
to application services and repositories. Public resource identifiers are UUIDs where
the route contract identifies a resource; principal-ID paths follow the canonical principal
string contract. Internal PostgreSQL numeric IDs never cross this boundary.

| Resource | Canonical route | Compatibility decision |
| --- | --- | --- |
| Buyer part requests | `/api/v1/part-requests` | Canonical and unchanged |
| Seller part listings | `/api/v1/part-listings` | Canonical and unchanged |
| Offers | `/api/v1/offers` | `/offers` retained as a compatibility alias until the next client migration; sunset 2027-04-01 |
| Files | `/api/v1/files` | `/images` and local folder browsing retired; use upload endpoints and UUID file reads |
| Buyer profile | `/api/v1/profiles/buyer` | `/customers/profile` retained as a compatibility alias until 2027-04-01 |
| Seller profile | `/api/v1/profiles/seller` | `/seller/profile` retained as a compatibility alias until 2027-04-01 |
| Buyer account deactivation | `PUT /api/v1/buyers/{publicId}/deactivate` | `PUT /customers/{publicId}/deactivate` retained as a compatibility alias until 2027-04-01 |
| Seller and wallet operations | `/api/v1/sellers/**` including seller create/read/deactivate and wallet add/deduct | Corresponding `/seller/**` paths retained as compatibility aliases until 2027-04-01 |
| Notifications | `/api/v1/notifications` | Versioned stream and command routes are canonical; `/notifications/*` remains as a deprecated compatibility alias until 2027-04-01 |
| Legacy piece reads | Marketplace part requests and listings | `/pieces/*` remains as a deprecated read-only compatibility surface until 2027-04-01; no mutation endpoint is supported |

Legacy aliases must preserve authentication, authorization, ownership, validation, and
DTO response behavior. New clients must use the canonical routes. The old `PieceController`
read surface is deprecated because part requests are the supported buyer aggregate. Its
former update contract was removed: it validated image IDs but never persisted a mutation.
File associations are created through the authenticated upload lifecycle instead.

The only versioned SSE route is `GET /api/v1/notifications/stream`. The unversioned
`/notifications/subscribe` and `/notifications/subscribe/{userId}` routes remain deprecated
until 2027-04-01; they are not mounted under `/api/v1`.

## Operation Inventory and OpenAPI Domain Ownership

Each active operation below belongs to exactly one proposed OpenAPI group. Compatibility
aliases share the canonical operation contract and are documented as deprecated until their
sunset. The inventory is transcribed from the current controller mappings; AP-0048 tests must
compare it with the mounted routes and security behavior so conditional controller/profile
settings cannot silently make the documentation stale.

| OpenAPI group | Active method and path | Compatibility/deprecation and access notes |
| --- | --- | --- |
| `identity` | `POST /auth/send-otp-email`; `POST /auth/validate-otp`; `GET /.well-known/jwks.json` | Intentional unversioned authentication/key-discovery routes. OTP and key material must not be examples beyond public JWKS. |
| `marketplace` | `POST, GET /api/v1/part-requests`; `POST /api/v1/part-requests/{publicId}/close`; `POST /api/v1/part-requests/{publicId}/cancel`; `GET /api/v1/part-requests/{publicId}/matches` | Buyer role; owner checks apply to identified requests. |
| `marketplace` | `POST, GET /api/v1/part-listings`; `POST /api/v1/part-listings/{publicId}/transition` | Seller role; seller ownership checks apply. |
| `marketplace` | `POST /api/v1/offers/requests/{requestId}`; `POST /api/v1/offers/listings/{listingId}`; `GET /api/v1/offers/{offerId}`; `PATCH /api/v1/offers/{offerId}/status` | `/offers/**` is a deprecated alias until 2027-04-01. Operations use their existing buyer/seller/authenticated role and participant checks. |
| `marketplace` | `PUT /api/v1/buyers/{id}/deactivate`; `POST /api/v1/sellers/create`; `GET /api/v1/sellers/{id}`; `POST /api/v1/sellers/{id}/deactivate` | Buyer alias `/customers/{id}/deactivate` and seller aliases `/seller/create`, `/seller/{id}`, `/seller/{id}/deactivate` sunset 2027-04-01. IDs are canonical user public UUIDs; self-deactivation requires role and ownership checks. |
| `marketplace` | `GET /pieces/{customerId}`; `GET /pieces/{pieceId}/detail` | Deprecated read-only compatibility operations until 2027-04-01. No mutation is supported. IDs resolve as public UUIDs; do not copy obsolete example IDs into OpenAPI. |
| `profiles` | `PUT /api/v1/profiles/buyer`; `PUT /api/v1/profiles/seller` | Aliases `PUT /customers/profile` and `PUT /seller/profile` sunset 2027-04-01. Requires the matching authenticated owner role. |
| `wallets` | `POST /api/v1/sellers/{id}/wallet/add`; `POST /api/v1/sellers/{id}/wallet/deduct` | Aliases `/seller/{id}/wallet/add` and `/seller/{id}/wallet/deduct` sunset 2027-04-01. Seller role and owner checks apply. Preserve amount/currency and idempotency semantics. |
| `files` | `POST /api/v1/files/requests/{requestId}`; `POST /api/v1/files/listings/{listingId}`; `GET /api/v1/files/{fileId}` | Buyer/seller role and resource ownership apply. `/images/**` and folder browsing are retired and are not operations. |
| `notifications` | `GET, PUT /api/v1/notifications/preferences`; `GET /api/v1/notifications/preferences/channel/{channel}`; `GET /api/v1/notifications/preferences/type/{type}` | Requires authentication and uses the authenticated user's preferences. |
| `notifications` | `GET /api/v1/notifications/stream` | Canonical SSE route. Event IDs/replay/ownership contract is in AP-0043; message payloads are never examples. |
| `notifications` | `POST /api/v1/notifications/fcm/register`; `POST /api/v1/notifications/fcm/send`; `POST /api/v1/notifications/fcm/notify/search`; `POST /api/v1/notifications/fcm/notify/response`; `POST /api/v1/notifications/fcm/urgent`; `GET /api/v1/notifications/fcm/stats/{userId}`; `GET /api/v1/notifications/fcm/health` | Register requires authentication; send/response/urgent/health are admin guarded; search is buyer guarded; stats is owner-or-admin. Never document device tokens or notification bodies as examples. |
| `notifications` | `POST /api/v1/notifications/broadcast`; `POST /api/v1/notifications/send` | Corresponding `/notifications/broadcast` and `/notifications/send` aliases sunset 2027-04-01. Admin-only. |
| `notifications` | `GET /notifications/subscribe`; `GET /notifications/subscribe/{userId}` | Deprecated unversioned SSE compatibility routes until 2027-04-01; they do not replace the canonical durable `/api/v1` stream. |
| `admin-audit` | `POST /api/v1/admin/audit/{eventId}/replay` | Admin-only operational action; document as such and preserve separate audit-store controls. |

`id`, `publicId`, `requestId`, `listingId`, `offerId`, `fileId`, and `eventId` path values must be documented as public UUIDs only where the current controller/service contract validates them as such. `userId` on FCM stats is an authenticated canonical-principal identifier; its public format must be verified before declaring a UUID schema. The existing controller method named `deactivateSeller` under `/api/v1/buyers/{id}/deactivate` deactivates a buyer, so generated operation summaries must describe actual behavior rather than copy the method name.

Some operation controllers are conditional on enabled PostgreSQL identity/data, Firebase, or audit configuration. The specification must represent the API contract for the intended application profile and tests must state which conditional routes are expected to be mounted; a disabled provider must not cause anonymous documentation or imply an unavailable operation is live.

## Canonical examples

```text
POST  /api/v1/part-requests
GET   /api/v1/part-requests?limit=50
POST  /api/v1/part-listings
POST  /api/v1/offers/requests/{requestPublicId}
POST  /api/v1/files/requests/{requestPublicId}
GET   /api/v1/files/{filePublicId}
PUT   /api/v1/profiles/buyer
PUT   /api/v1/profiles/seller
POST  /api/v1/sellers/{sellerPublicId}/wallet/add
GET   /api/v1/notifications/stream
```

All routes use the safe API error contract. Missing resources return 404, malformed UUIDs
and invalid request values return 400, unauthenticated requests return 401, and ownership
or role failures return 403.
