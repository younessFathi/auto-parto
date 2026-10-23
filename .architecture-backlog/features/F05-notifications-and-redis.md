# F05 Notifications and Redis

Persist notification intent durably in PostgreSQL and use Redis as a bounded, rebuildable delivery projection.

**Status:** In Progress

- [AP-0012](../tickets/AP-0012-notification-outbox.md): Persist transactional notification outbox records. **Done**
- [AP-0013](../tickets/AP-0013-outbox-processing.md): Process outbox work with bounded leases and retries. **Done**
- [AP-0016](../tickets/AP-0016-redis-projections.md): Maintain Redis delivery projections and recovery. **Done**
- [AP-0042](../tickets/AP-0042-notification-delivery-reliability.md): Complete durable notification delivery and recovery reliability. **In Progress**

## Retired or absorbed references

AP-0014, AP-0015, and AP-0038 were never created as active tickets. Device-token persistence is covered by AP-0002/AP-0016. Remaining durable SSE/FCM delivery acknowledgement, retry, and recovery work is tracked by AP-0042.

## Completion Tracking

PostgreSQL owns durable notification records, delivery claims, attempts, leases, retry timing, failure classifications, and exhaustion. Redis provides bounded, expiring projections and delivery acceleration; it is rebuilt from PostgreSQL after loss or restart and is never the only recoverable copy. AP-0042 now covers offline SSE preservation, enabled scheduled recovery, idempotent projection replacement, cancellation-owned recovery, prompt cancellation on sink replacement, and completion of replaced SSE connections; the feature remains In Progress pending owner review.
