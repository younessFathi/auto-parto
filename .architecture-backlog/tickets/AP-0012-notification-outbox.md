# AP-0012 Persist Transactional Notification Outbox

- **Status:** Done
- **Type:** Technical Enhancement
- **Feature:** [Notifications and Redis](../features/F05-notifications-and-redis.md)
- **Linked Tickets:** [AP-0001](AP-0001-postgresql-schema.md), [AP-0002](AP-0002-reactive-persistence.md)
- **Source:** `architecture.md` Notification Architecture

## Description
Persist each business change and required notification intent in one PostgreSQL transaction before projection or delivery.

## Implementation Tracking

The first implementation slice makes PostgreSQL notification enqueue authoritative: the notification record and its versioned `notification.requested` outbox event are inserted in one transaction before Redis projection. The existing database trigger creates the durable `AUDIT` delivery row for the event. Outbox claiming, retries, replay, and provider delivery remain with AP-0013 and the later notification-delivery tickets.

The AP-0012 review follow-up enforces a 128 KiB UTF-8 limit for serialized notification payloads, recovers pending recipients through stable UUID keyset batches of 100, and verifies that notification and outbox rows roll back together when outbox persistence fails.

## Acceptance Criteria
1. Required business changes and outbox events commit atomically.
2. Events have stable IDs, versioned payloads, timestamps, and durable state.
3. No recoverable notification depends only on Redis enqueue.
4. Projection and delivery can restart from PostgreSQL.

## Test Scenarios
1. **Given** outbox persistence failure, **when** a required business change runs, **then** both changes roll back.
2. **Given** a committed event, **when** the application restarts, **then** the event remains claimable.
3. **Given** Redis loss, **when** recovery runs, **then** the event can be projected again.

## AP-0001 Review Follow-up

When creating an outbox event, create the required `outbox_deliveries` rows in the same transaction. Every event needs exactly one durable `AUDIT` destination row; notification destinations must be added according to the event policy. Populate and validate `aggregate_public_id` for aggregate-scoped events and preserve stable event and delivery IDs across retries.

## Definition of Done
The whole ticket is implemented, every acceptance criterion is satisfied, every scenario is automated and passing, transaction and restart coverage is complete, and all applicable suites pass.
