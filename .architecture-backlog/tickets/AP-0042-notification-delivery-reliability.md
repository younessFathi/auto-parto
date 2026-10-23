# AP-0042 Complete Notification Delivery Reliability

- **Status:** Proposed
- **Type:** Technical Enhancement
- **Feature:** [Notifications and Redis](../features/F05-notifications-and-redis.md)
- **Linked Tickets:** [AP-0012](AP-0012-notification-outbox.md), [AP-0013](AP-0013-outbox-processing.md), [AP-0016](AP-0016-redis-projections.md), [AP-0028](AP-0028-provider-isolation.md)

## Problem

The notification path has durable PostgreSQL intent and bounded Redis projections, but the direct SSE fallback still has reliability gaps: a store result can be treated as successful without proving persistence, and a reactive persistence operation can be discarded before execution. The catalog must keep F05 open until these behaviors are corrected and verified.

## Scope

1. Make durable pending-notification persistence part of the returned reactive delivery chain.
2. Preserve bounded queues, retry limits, replay behavior, and shutdown safety across SSE and FCM delivery.
3. Define safe endpoint behavior when durable notification persistence fails.
4. Add unit, HTTP, and PostgreSQL/Redis integration coverage for false persistence results, discarded chains, retry exhaustion, replay, and recovery.

## Acceptance Criteria

- [ ] A notification is acknowledged as stored only after the durable PostgreSQL operation succeeds.
- [ ] Every asynchronous persistence chain is subscribed by an owned lifecycle or returned to its caller; no delivery operation is silently discarded.
- [ ] Retry, replay, projection bounds, and shutdown behavior remain bounded and documented.
- [ ] Endpoint responses use the established safe error contract when storage or provider delivery fails.
- [ ] Focused unit, HTTP, and container-backed tests cover success, failure, duplicate, retry-exhaustion, and recovery paths.

## Definition of Done

The notification delivery path reports only verified durable outcomes, bounded recovery behavior is tested, and F05 can be marked Done based on implementation evidence.
