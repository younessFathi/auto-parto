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
| `GET` | `/api/v1/part-requests` | List the authenticated buyer's requests |
| `POST` | `/api/v1/part-requests/{publicId}/close` | Close an owned open request |
| `POST` | `/api/v1/part-requests/{publicId}/cancel` | Cancel an owned open request |

Creation accepts a required `name` and optional description, condition, category,
vehicle make, vehicle model, and vehicle year. The response contains the request's
public UUID, the buyer's public UUID, searchable fields, lifecycle status, timestamps,
and optimistic version. Internal database identifiers are not exposed.

Requests start in `OPEN` status. Only the owner may close or cancel a request, and
both transitions are allowed only from `OPEN`; an ownership violation returns `403`
and an invalid lifecycle transition returns `409`.
