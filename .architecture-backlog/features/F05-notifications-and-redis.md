# F05 Notifications and Redis

Persist notification intent durably in PostgreSQL and use Redis as a bounded, rebuildable delivery projection.

**Status:** In Progress

- [AP-0012](../tickets/AP-0012-notification-outbox.md): Persist transactional notification outbox records. **Done**
- [AP-0013](../tickets/AP-0013-outbox-processing.md): Process outbox work with bounded leases and retries. **Done**
- [AP-0016](../tickets/AP-0016-redis-projections.md): Maintain Redis delivery projections and recovery. **Done**
- [AP-0042](../tickets/AP-0042-notification-delivery-reliability.md): Complete durable notification delivery and recovery reliability. **Proposed**

## Retired or absorbed references

AP-0014, AP-0015, and AP-0038 were never created as active tickets. Device-token persistence is covered by AP-0002/AP-0016. Remaining durable SSE/FCM delivery acknowledgement, retry, and recovery work is tracked by AP-0042.

## Completion Tracking

PostgreSQL owns durable notification records and outbox recovery. Redis provides bounded projections and delivery acceleration; it is not the source of truth. PostgreSQL owns notification intent and outbox recovery, while Redis provides bounded projections. Durable SSE/FCM acknowledgement, retry/exhaustion behavior, and end-to-end recovery remain open under AP-0042; this feature must not be treated as complete until that ticket passes.
