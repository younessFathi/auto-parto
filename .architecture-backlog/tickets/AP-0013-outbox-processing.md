# AP-0013 Process Outbox Work Safely

- **Status:** Done
- **Type:** Technical Enhancement
- **Feature:** [Notifications and Redis](../features/F05-notifications-and-redis.md)
- **Linked Tickets:** [AP-0012](AP-0012-notification-outbox.md)
- **Source:** `deployment-architecture.md` Redis Persistence and Durability

## Description
Claim outbox events across instances with expiring leases, bounded retries, and observable exhaustion.

## Implementation Tracking

Started on `feat/ap-0013-outbox-processing` from the latest `origin/main`. The existing audit outbox publisher and PostgreSQL delivery repository are the starting boundary; this ticket will complete and verify lease claims, retry/exhaustion state, authorized replay, ordering behavior, and reconciliation against the independent audit store.

The implementation now adds a PostgreSQL audit-store adapter, operator authorization at the replay boundary, correct maximum-attempt handling, bounded terminal-delivery cleanup, and a two-PostgreSQL Testcontainers publisher integration suite. Audit selection order is documented as `available_at, id`; cross-worker aggregate ordering is intentionally not promised until a serialized stream policy is introduced.

Review follow-up for commit `8bb08b1` adds conditional production composition: separate audit R2DBC credentials, the bounded `SmartLifecycle` worker, scheduled reconciliation and retention cleanup, and an administrator-only replay endpoint. The retention API derives its cutoff from `now - 7 days`, and the two-PostgreSQL integration suite covers worker execution, persisted retry/exhaustion, and reconciliation of a missing audit event.

Review follow-up for commit `b5b2101` adds the missing least-privilege boundary: `audit_writer` remains a NOLOGIN capability role with only append, sequence, and reconciliation-read access, while deployments provision a separate login runtime principal through protected secret management. Enabled audit settings are validated before composition, and the worker performs an audit-database readiness probe before it can claim application deliveries. Tests cover constrained-role append/read access, denied mutation, invalid enabled configuration, and unavailable-audit worker safety.

Review follow-up for commit `046f826` preserves the original V1 audit migration and adds V2 for the writer reconciliation SELECT grant, so existing audit volumes upgrade through normal Flyway migration without checksum repair or history changes. Enabled audit processing exposes the `auditReadiness` health indicator with a bounded probe timeout; it remains DOWN while audit PostgreSQL is unavailable. Each bounded worker operation checks the same readiness contract before claiming, and the worker stops cleanly without an unbounded retry queue. Testcontainers cover V1-to-V2 upgrade privileges and zero claims while the audit database is stopped.

Review follow-up for commit `3f778f3` includes `auditReadiness` and `readinessState` in Spring Boot's standard readiness health group and enables health probes, so `/actuator/health/readiness` returns HTTP 503/DOWN while the enabled audit database is unavailable and HTTP 200/UP after recovery. An application-level Testcontainers test exercises the real actuator endpoint across audit PostgreSQL stop and restart; the worker claim-safety coverage remains separate.

The readiness application test uses Testcontainers' randomized host-port mapping and pauses/unpauses the existing audit PostgreSQL container to simulate failure and recovery without reconnecting through a changed port or conflicting with local services.

## Acceptance Criteria
1. Workers claim events atomically with an expiring lease.
2. Expired claims are recoverable after worker failure.
3. Attempts, backoff, and retry count are bounded.
4. Poison or exhausted events are retained durably for a documented bounded period with authorized replay.
5. Processing is explicitly at-least-once and duplicate-safe.
6. Ordering requirements are defined per event stream and violations are observable.

## Test Scenarios
1. **Given** two workers, **when** they claim one event, **then** only one owns the active lease.
2. **Given** a crashed worker, **when** its lease expires, **then** another worker can claim the event.
3. **Given** retry exhaustion, **when** the last attempt fails, **then** the event becomes exhausted and observable.
4. **Given** events requiring ordering, **when** workers process concurrently, **then** the documented order is preserved or violations are detected.

## AP-0001 Review Follow-up

Process `outbox_deliveries` by delivery ID and destination, not only by event ID. Implement atomic cross-instance claims, lease expiry recovery, bounded retries, exhaustion, replay, and reconciliation for the independent `AUDIT` delivery lifecycle. Persist only controlled error classifications, never raw provider or database messages.

## Integration Test Requirement

Add a Docker/Testcontainers integration test that runs `AuditPublisher` against real PostgreSQL-backed outbox and audit-store adapters, using separate application and audit PostgreSQL containers. Cover successful delivery, duplicate event IDs, persisted retry and exhaustion state, lease expiry recovery, concurrent worker claims, authorized replay, and reconciliation. The test must not use in-memory fakes for the persistence or lease behavior under test.

## Definition of Done
The whole ticket is implemented, every acceptance criterion is satisfied, every scenario is automated and passing, multi-instance and recovery tests are complete, and all applicable suites pass.
