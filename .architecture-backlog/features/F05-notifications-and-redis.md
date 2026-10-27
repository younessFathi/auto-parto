# F05 Notifications and Redis

Persist notification intent durably in PostgreSQL and use Redis as a bounded, rebuildable delivery projection.

**Status:** Done

- [AP-0012](../tickets/AP-0012-notification-outbox.md): Persist transactional notification outbox records. **Done**
- [AP-0013](../tickets/AP-0013-outbox-processing.md): Process outbox work with bounded leases and retries. **Done**
- [AP-0016](../tickets/AP-0016-redis-projections.md): Maintain Redis delivery projections and recovery. **Done**
- [AP-0042](../tickets/AP-0042-notification-delivery-reliability.md): Complete durable notification delivery and recovery reliability. **Done**
- [AP-0043](../tickets/AP-0043-versioned-sse-delivery-contract.md): Define the canonical versioned SSE delivery contract. **Done**

## Retired or absorbed references

AP-0014, AP-0015, and AP-0038 were never created as active tickets. Device-token persistence is covered by AP-0002/AP-0016. Remaining durable SSE/FCM delivery acknowledgement, retry, and recovery work is tracked by AP-0042.

## Completion Tracking

PostgreSQL owns durable notification records, delivery claims, attempts, leases, retry timing, failure classifications, and exhaustion. Redis provides bounded, expiring projections and delivery acceleration; it is rebuilt from PostgreSQL after loss or restart and is never the only recoverable copy. AP-0042 covers durable recovery; AP-0043 defines the canonical versioned SSE contract, bounded replay, persisted event IDs, and the current instance-affinity routing constraint. The feature owner accepted AP-0042 on 2026-10-26 after reviewing its persistence, recovery, bounded projection, delivery lifecycle, and safe-output evidence. All F05 delivery tickets are complete. The separate Firebase provider scheduler isolation and saturation limitation remains tracked by [AP-0028](../tickets/AP-0028-provider-isolation.md).
