# Transactional Notification Outbox

When PostgreSQL notification persistence is enabled, creating a notification is a durable database operation. The application inserts the pending notification and a versioned `notification.requested` event in one PostgreSQL transaction. The event records the notification public ID as its aggregate identity and includes the notification type and JSON payload in a bounded JSON object.

The database creates one `AUDIT` delivery row for every outbox event. The notification row remains the authoritative pending record. Redis is a rebuildable delivery projection: it is populated only after the PostgreSQL transaction commits, and pending records can be recovered from PostgreSQL if Redis is unavailable or loses data.

Outbox event claiming, lease expiry, retry, exhaustion, replay, and provider delivery are separate processing concerns covered by AP-0013 and the later notification delivery tickets. This boundary does not send to an external provider inside the request transaction.
