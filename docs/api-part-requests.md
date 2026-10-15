# Buyer Part Requests API

The part request API is available when `auto.parto.persistence.postgresql.enabled=true`.
All endpoints require a canonical authenticated JWT with the `BUYER` role. The buyer
identity is taken from the authenticated principal; request payloads do not select an
owner.

## Endpoints

Base path: `/api/v1/part-requests`

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/api/v1/part-requests` | Create an owned part request |
| `GET` | `/api/v1/part-requests?limit=50` | List the authenticated buyer's requests |
| `POST` | `/api/v1/part-requests/{publicId}/close` | Close an owned open request |
| `POST` | `/api/v1/part-requests/{publicId}/cancel` | Cancel an owned open request |

Creation accepts a required `name` and optional description, condition, category,
vehicle make, vehicle model, and vehicle year. The response contains the request's
public UUID, the buyer's public UUID, searchable fields, lifecycle status, timestamps,
and optimistic version. Internal database identifiers are not exposed.

Requests start in `OPEN` status. Only the owner may close or cancel a request, and
both transitions are allowed only from `OPEN`; an ownership violation returns `403`
and an invalid lifecycle transition returns `409`.

The list endpoint accepts an optional opaque `cursor` and a `limit` from `1` through
`100`. The default limit is `50`; invalid limits or malformed cursors return `400`.
The response has an `items` array and an optional `nextCursor`. Pass `nextCursor` as
the next request's `cursor` until it is `null`.

Results use the stable order `created_at DESC, public_id DESC`. The cursor encodes the
last returned item's creation timestamp and public UUID. PostgreSQL uses keyset
predicates for subsequent requests, so no `OFFSET` scan is needed and tied timestamps
remain deterministic.
