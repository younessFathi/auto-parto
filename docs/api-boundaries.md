# API Boundary and Route Catalog

The public API uses versioned `/api/v1` routes. Controllers accept and return DTOs,
derive ownership from the authenticated canonical principal, and pass persistence work
to application services and repositories. Resource identifiers in public paths are UUIDs;
internal PostgreSQL numeric IDs never cross this boundary.

| Resource | Canonical route | Compatibility decision |
| --- | --- | --- |
| Buyer part requests | `/api/v1/part-requests` | Canonical and unchanged |
| Seller part listings | `/api/v1/part-listings` | Canonical and unchanged |
| Offers | `/api/v1/offers` | `/offers` retained as a compatibility alias until the next client migration; sunset 2027-04-01 |
| Files | `/api/v1/files` | `/images` and local folder browsing retired; use upload endpoints and UUID file reads |
| Buyer profile | `/api/v1/profiles/buyer` | `/customers/profile` retained as a compatibility alias until 2027-04-01 |
| Seller profile | `/api/v1/profiles/seller` | `/seller/profile` retained as a compatibility alias until 2027-04-01 |
| Buyer status | `/api/v1/buyers/{publicId}` | `/customers/{publicId}` retained as a compatibility alias until 2027-04-01 |
| Seller and wallet operations | `/api/v1/sellers/{publicId}` and `/api/v1/sellers/{publicId}/wallet/*` | `/seller/*` retained as a compatibility alias until 2027-04-01 |
| Notifications | `/api/v1/notifications` | Versioned stream and command routes are canonical; `/notifications/*` remains only as a deprecated compatibility alias |

Legacy aliases must preserve authentication, authorization, ownership, validation, and
DTO response behavior. New clients must use the canonical routes. The old `PieceController`
read surface is deprecated because part requests are the supported buyer aggregate. Its
former update contract was removed: it validated image IDs but never persisted a mutation.
File associations are created through the authenticated upload lifecycle instead.

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
