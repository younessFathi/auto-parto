# Transactional Notification Outbox

When PostgreSQL notification persistence is enabled, creating a notification is a durable database operation. The application inserts the pending notification and a versioned `notification.requested` event in one PostgreSQL transaction. The event records the notification public ID as its aggregate identity and includes the notification type and JSON payload in a JSON object. The serialized notification payload is limited to 128 KiB in UTF-8 before either database row is written.

The database creates one `AUDIT` delivery row for every outbox event. The notification row remains the authoritative pending record. Redis is a rebuildable delivery projection: it is populated only after the PostgreSQL transaction commits, and pending records can be recovered from PostgreSQL if Redis is unavailable or loses data. Startup recovery walks pending recipient IDs in stable UUID-keyset batches of 100, so the batch size bounds memory without omitting later recipients.

Outbox event claiming, lease expiry, retry, exhaustion, replay, and provider delivery are separate processing concerns covered by AP-0013 and the later notification delivery tickets. This boundary does not send to an external provider inside the request transaction.
