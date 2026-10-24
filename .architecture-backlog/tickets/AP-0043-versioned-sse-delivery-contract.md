# AP-0043 Versioned SSE Delivery Contract

- **Status:** In Progress
- **Type:** Technical Enhancement
- **Feature:** [Notifications and Redis](../features/F05-notifications-and-redis.md)
- **Linked Tickets:** [AP-0042](AP-0042-notification-delivery-reliability.md), [AP-0012](AP-0012-notification-outbox.md), [AP-0016](AP-0016-redis-projections.md)
- **Source:** `docs/architecture.md` Notification Architecture

## Description

Define one canonical versioned SSE contract over the PostgreSQL-authoritative
notification state. The contract uses the persisted notification public UUID as the
event ID, supports bounded `Last-Event-ID` replay and client deduplication, protects
canonical principal ownership, and documents the instance-affinity routing required by
the current JVM-local connection registry.

Legacy unversioned SSE endpoints are explicitly deprecated while clients migrate to
`GET /api/v1/notifications/stream`.

## Acceptance Criteria

1. The versioned endpoint derives the recipient from the authenticated canonical principal and never trusts a path user ID.
2. SSE event IDs are persisted notification public UUIDs for live and replayed events; delivery never generates a replacement event ID.
3. `Last-Event-ID` replay is bounded, PostgreSQL-authoritative, ordered chronologically by `created_at, public_id`, and rejects missing or foreign cursors with the safe 400 contract.
4. Durable notification state and SSE transport attempts remain separate; socket writes do not imply client receipt or processing.
5. Connection limits and bounded per-connection buffers produce controlled documented behavior without null responses or unhandled 500 errors.
6. Heartbeats, cleanup, timeout handling, and structured safe logging remain bounded and reactive.
7. Multi-instance behavior documents instance affinity for the JVM-local connection map and the required routing constraint.
8. Controller, security, PostgreSQL, and lifecycle tests cover replay, duplicate IDs, cursor ownership, caps, overflow, reconnect cleanup, direct delivery acknowledgement, and the documented routing behavior.

## Review follow-up

The versioned stream validates the cursor against the authenticated recipient before
registering a connection and uses the recipient replay index added by V13. Replay moves
forward from `Last-Event-ID` in chronological keyset order. Live delivery persists, claims,
records the SSE attempt, and acknowledges the same PostgreSQL row after a successful write;
failed writes keep that one row retryable. Heartbeats are owned by the exact registered sink,
so cancellation or replacement closes the old response and runs matching-sink cleanup. The
ticket remains In Progress until the full unit and Docker-backed verification suites pass.

## Test Scenarios

1. **Given** persisted notifications and a valid `Last-Event-ID`, **when** the owner reconnects, **then** a bounded ordered replay uses the persisted public UUIDs as event IDs.
2. **Given** a different authenticated principal, **when** it connects to the versioned stream, **then** the request is rejected and no connection is registered for another user.
3. **Given** the connection or buffer cap is reached, **when** a client connects or a delivery is attempted, **then** the API returns a controlled status or the durable notification remains retryable.
4. **Given** a rapid reconnect or client cancellation, **when** the old sink terminates, **then** only its recovery and cleanup are cancelled while the replacement remains registered.
5. **Given** two application instances, **when** a client reconnects, **then** delivery follows the documented instance-affinity routing constraint.

## Definition of Done

The complete ticket is implemented; every acceptance criterion is satisfied; every test scenario is automated and passing; API, architecture, backlog, and trace documentation are updated; and `./mvnw -B test` plus `./mvnw -B verify` pass with Docker.
