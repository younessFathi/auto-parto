# F05 Notifications and Redis

Persist notification intent durably in PostgreSQL and use Redis as a bounded, rebuildable delivery projection.

**Status:** Done

- [AP-0012](../tickets/AP-0012-notification-outbox.md): Persist transactional notification outbox records. **Done**
- [AP-0013](../tickets/AP-0013-outbox-processing.md): Process outbox work with bounded leases and retries. **Done**
- [AP-0016](../tickets/AP-0016-redis-projections.md): Maintain Redis delivery projections and recovery. **Done**

## Retired or absorbed references

AP-0014 and AP-0015 were never created as active tickets. Their device-token, SSE, and FCM responsibilities are covered by AP-0002, AP-0005, AP-0012, AP-0013, AP-0016, and the provider-isolation scope in AP-0028. AP-0038 was likewise not created; its provider-neutral device-token work is implemented in AP-0002 and AP-0016.

## Completion Tracking

PostgreSQL owns durable notification records and outbox recovery. Redis provides bounded projections and delivery acceleration; it is not the source of truth. FCM and SSE delivery, retry/exhaustion behavior, readiness, and recovery coverage are implemented and verified.
