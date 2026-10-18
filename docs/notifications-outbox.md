# Transactional Notification Outbox

When PostgreSQL notification persistence is enabled, creating a notification is a durable database operation. The application inserts the pending notification and a versioned `notification.requested` event in one PostgreSQL transaction. The event records the notification public ID as its aggregate identity and includes the notification type and JSON payload in a JSON object. The serialized notification payload is limited to 128 KiB in UTF-8 before either database row is written.

The database creates one `AUDIT` delivery row for every outbox event. The notification row remains the authoritative pending record. Redis is a rebuildable delivery projection: it is populated only after the PostgreSQL transaction commits, and pending records can be recovered from PostgreSQL if Redis is unavailable or loses data. Startup recovery walks pending recipient IDs in stable UUID-keyset batches of 100, so the batch size bounds memory without omitting later recipients.

Outbox event claiming, lease expiry, retry, exhaustion, replay, and provider delivery are separate processing concerns covered by AP-0013 and the later notification delivery tickets. This boundary does not send to an external provider inside the request transaction.

## AP-0013 processing contract

Set `APP_AUDIT_OUTBOX_ENABLED=true` only in a deployment where the separate audit PostgreSQL schema has been migrated and the application has a least-privilege audit writer credential. Configure `AUDIT_POSTGRES_R2DBC_URL`, `AUDIT_POSTGRES_RUNTIME_USER`, and `AUDIT_POSTGRES_RUNTIME_PASSWORD` separately from the application database. The application starts one bounded lifecycle worker per instance; PostgreSQL leases ensure instances claim different deliveries. `/api/v1/admin/audit/{eventId}/replay` is restricted to authenticated administrators.

The audit worker claims `outbox_deliveries` rows by destination with PostgreSQL `FOR UPDATE SKIP LOCKED`. Claims use a bounded lease and are recoverable after expiry. Processing is at least once: the audit store is idempotent on the stable event UUID, and a worker crash after the audit write can cause a duplicate acknowledgement rather than a duplicate audit row.

Each claim increments the bounded attempt counter. Retryable failures use capped exponential backoff; permanent failures and retry exhaustion become durable `EXHAUSTED` state with one of the controlled error classifications. Terminal audit delivery rows are eligible for bounded cleanup after the seven-day reconciliation retention window. Replay requires an operator-authorized caller and reuses the original event UUID.

Audit delivery is ordered by `available_at, id` when claims are selected. Audit events do not promise cross-worker or cross-aggregate ordering; streams that require ordering must be assigned a serialized destination policy before they are introduced. Claim, retry, exhaustion, replay, and reconciliation outcomes are returned in bounded publisher reports and remain queryable in PostgreSQL for operational inspection.
