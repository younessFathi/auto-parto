# AP-0016 Bound and Rebuild Redis Projections

- **Status:** Done
- **Type:** Technical Enhancement
- **Feature:** [Notifications and Redis](../features/F05-notifications-and-redis.md)
- **Linked Tickets:** [AP-0012](AP-0012-notification-outbox.md), [AP-0013](AP-0013-outbox-processing.md), [AP-0002](AP-0002-reactive-persistence.md)
- **Source:** `architecture.md` Redis Design; `deployment-architecture.md` Redis Persistence

## Description
Classify, bound, atomically update, and reconstruct every Redis key family without making Redis a business source of truth.

## Acceptance Criteria
1. Each key family defines classification, TTL, maximum size, eviction, and failure behavior.
2. Multi-step operations are atomic and retries are deduplicated.
3. Notification and device-token projections rebuild from PostgreSQL after loss.
4. Abuse-sensitive rate limits fail closed or use a bounded fallback.

## Test Scenarios
1. **Given** a full collection, **when** another item arrives, **then** the documented bound is enforced.
2. **Given** empty Redis, **when** recovery runs, **then** active projections rebuild from PostgreSQL.
3. **Given** Redis failure, **when** login or OTP is attempted, **then** the configured secure policy applies.

## Definition of Done
The whole ticket is implemented, every acceptance criterion is satisfied, every scenario is automated and passing, Redis failure and restart tests are complete, and all applicable suites pass.

## Implementation tracking

- Pending notification lists are capped at 100 entries and use an atomic append/trim/TTL Lua operation.
- Retry and dead-letter projections are bounded at 100 entries; dead letters expire after seven days.
- Statistics and cleanup use bounded `SCAN` operations; PostgreSQL remains authoritative and Redis projections remain rebuildable.
- `RedisNotificationStoreIT` verifies that over-capacity writes retain only the configured bound, and device-token recovery coverage remains in place.
