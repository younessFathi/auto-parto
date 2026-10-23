# AP-0029 Add CloudEvents Interoperability Contract

- **Status:** Blocked
- **Type:** Technical Enhancement
- **Feature:** [Notifications and Redis](../features/F05-notifications-and-redis.md)
- **Linked Tickets:** [AP-0012](AP-0012-notification-outbox.md), [AP-0013](AP-0013-outbox-processing.md)
- **Source:** `deployment-architecture.md` CloudEvents Decision

## Description
Introduce CloudEvents only when a second service, external consumer, webhook, or broker requires a stable interoperable event contract. This ticket is blocked until that need exists.

## Acceptance Criteria
1. A documented external consumer and interoperability requirement justify activation.
2. Events are published from the PostgreSQL outbox only after the business transaction commits.
3. Each event includes UUID ID, type, source, subject, time, schema version, and correlation/trace context.
4. Event schemas are versioned and consumers are idempotent.
5. CloudEvents are never used as a logging format.

## Test Scenarios
1. **Given** no external contract need, **when** architecture is implemented, **then** internal versioned events remain sufficient and this ticket stays blocked.
2. **Given** a committed outbox event, **when** published, **then** its CloudEvent envelope contains every required attribute.
3. **Given** duplicate delivery, **when** a consumer handles the same event ID, **then** business effects occur once.

## Definition of Done
The whole ticket is implemented only after its unblock condition exists; every acceptance criterion is satisfied; every scenario is automated and passing; schemas and consumer guidance are documented; and all applicable suites pass.
