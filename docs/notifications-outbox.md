# Transactional Notification Outbox

When PostgreSQL notification persistence is enabled, creating a notification is a durable database operation. The application inserts the pending notification and a versioned `notification.requested` event in one PostgreSQL transaction. The event records the notification public ID as its aggregate identity and includes the notification type and JSON payload in a JSON object. The serialized notification payload is limited to 128 KiB in UTF-8 before either database row is written.

The database creates one `AUDIT` delivery row for every outbox event. The notification row remains the authoritative pending record. Redis is a rebuildable delivery projection: it is populated only after the PostgreSQL transaction commits, and pending records can be recovered from PostgreSQL if Redis is unavailable or loses data. Startup recovery walks pending recipient IDs in stable UUID-keyset batches of 100, so the batch size bounds memory without omitting later recipients.

Outbox event claiming, lease expiry, retry, exhaustion, replay, and provider delivery are separate processing concerns covered by AP-0013 and the later notification delivery tickets. This boundary does not send to an external provider inside the request transaction.

## AP-0013 processing contract

Set `APP_AUDIT_OUTBOX_ENABLED=true` only in a deployment where the separate audit PostgreSQL schema has been migrated and the application has a least-privilege audit runtime credential. V1 creates `audit_writer` as a NOLOGIN capability role; V2 grants it the audit-event SELECT privilege needed for reconciliation while preserving INSERT, sequence, and append-only mutation protections. Provision a login-capable runtime principal through the protected secret system and grant it membership in `audit_writer`. Configure `AUDIT_POSTGRES_R2DBC_URL`, `AUDIT_POSTGRES_RUNTIME_USER`, and `AUDIT_POSTGRES_RUNTIME_PASSWORD` separately from the application database. Existing audit volumes upgrade by applying V2 through Flyway; V1 and its history record are left unchanged. Enabled processing validates these settings before startup, and the `auditReadiness` health indicator remains DOWN until the audit database responds within the bounded readiness timeout. The worker checks the same readiness contract before each bounded operation, so it cannot claim application deliveries while audit PostgreSQL is unavailable. `/api/v1/admin/audit/{eventId}/replay` is restricted to authenticated administrators.

The audit worker claims `outbox_deliveries` rows by destination with PostgreSQL `FOR UPDATE SKIP LOCKED`. Claims use a bounded lease and are recoverable after expiry. Processing is at least once: the audit store is idempotent on the stable event UUID, and a worker crash after the audit write can cause a duplicate acknowledgement rather than a duplicate audit row.

Each claim increments the bounded attempt counter. Retryable failures use capped exponential backoff; permanent failures and retry exhaustion become durable `EXHAUSTED` state with one of the controlled error classifications. Terminal audit delivery rows are eligible for bounded cleanup after the seven-day reconciliation retention window. Replay requires an operator-authorized caller and reuses the original event UUID.

Audit delivery is ordered by `available_at, id` when claims are selected. Audit events do not promise cross-worker or cross-aggregate ordering; streams that require ordering must be assigned a serialized destination policy before they are introduced. Claim, retry, exhaustion, replay, and reconciliation outcomes are returned in bounded publisher reports and remain queryable in PostgreSQL for operational inspection.

## Canonical SSE delivery

Clients use `GET /api/v1/notifications/stream` with the authenticated canonical
principal. The response is a bounded SSE stream with a 30-second heartbeat. Event IDs
are the persisted notification public UUIDs, including replayed events; the request
payload's `id` is not a substitute. On reconnect, `Last-Event-ID` is an opaque UUID
cursor. At most 100 PostgreSQL rows strictly after that event are replayed in
`created_at, public_id` order. The cursor must belong to the authenticated
recipient; malformed, missing, or foreign cursors return 400. Clients deduplicate IDs
because a live event and a replayed event may overlap. A full instance connection
registry returns 429.

The durable notification row and its SSE transport attempt remain separate. Writing to
the socket records a bounded `WRITTEN` attempt but does not mean the client received or
processed the event. The current connection registry is process-local, so deployments
must use load-balancer instance affinity for an SSE connection and reconnect. A future
shared connection coordinator is required before claiming global multi-instance live
delivery. The legacy `/notifications/subscribe` and `/notifications/subscribe/{userId}`
routes are deprecated during migration.
