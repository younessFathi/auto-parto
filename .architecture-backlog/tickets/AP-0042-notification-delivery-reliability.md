# AP-0042 Complete Notification Delivery Reliability

- **Status:** In Progress
- **Type:** Technical Enhancement
- **Feature:** [Notifications and Redis](../features/F05-notifications-and-redis.md)
- **Linked Tickets:** [AP-0012](AP-0012-notification-outbox.md), [AP-0013](AP-0013-outbox-processing.md), [AP-0016](AP-0016-redis-projections.md), [AP-0028](AP-0028-provider-isolation.md)

## Delivered scope

PostgreSQL is authoritative for notification intent and delivery state. Notification
claims use bounded `FOR UPDATE SKIP LOCKED` leases, and PostgreSQL stores attempt counts,
next-attempt times, claim ownership, lease expiry, controlled failure codes, and terminal
exhaustion. The current policy permits three attempts. Redis is a bounded, expiring,
rebuildable delivery projection; it is populated after durable persistence and rebuilt
from PostgreSQL after Redis loss or application restart. JVM-local queues and Redis-only
retry state are not used.

The HTTP fallback now returns the actual persistence result. Background recovery has an
explicit owned subscription, and all delivery claims, acknowledgements, and retry state
changes remain in the reactive persistence chain. Error responses and logs use safe
classification codes without notification payloads or provider exception text.

## Acceptance criteria

- [x] A notification is acknowledged as stored only after the durable PostgreSQL operation succeeds.
- [x] Every asynchronous persistence chain is subscribed by an owned lifecycle or returned to its caller.
- [x] Retry, replay, projection bounds, and shutdown behavior remain bounded and are documented.
- [x] Endpoint responses use the established safe error contract when storage or provider delivery fails.
- [x] Unit, HTTP, and container-backed tests cover persistence failure, Redis failure, concurrent claims, retry exhaustion, duplicate-safe acknowledgement, offline preservation, scheduled recovery, SSE cancellation ownership, and idempotent projection replacement.

## Verification

Focused unit and HTTP tests cover persistence result propagation, reactive delivery,
SSE cancellation ownership, server-side sink completion on reconnect, projection failure,
offline recovery guards, and FCM failure classification. PostgreSQL/Testcontainers
coverage verifies bounded claims, lease recovery, deterministic retry exhaustion, and
schema migration. Redis integration coverage verifies bounded projection reads and
idempotent projection replacement. A Spring context test verifies scheduled recovery is
active. The implementation is complete pending the feature owner’s review and status
transition.
