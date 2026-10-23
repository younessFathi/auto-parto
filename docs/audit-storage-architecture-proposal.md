# Audit Storage Separation Proposal

## Status

Finalized architecture decision. The primary application database and the audit database are separate production resources with independent compute, storage, credentials, backups, monitoring, and failure boundaries.

## Decision Summary

Auto-Parto should separate high-volume audit history from the primary application database and run it on an independently managed audit storage system.

The primary PostgreSQL database remains authoritative for business state and financial records. Audit events are handed off through a PostgreSQL outbox in the same transaction as the business change, then delivered asynchronously to the dedicated audit database.

This avoids putting the full audit workload, retention period, indexes, and reporting queries on the application database while preserving recovery after application or queue failures.

## Why This Separation Is Needed

Audit and application data have different workloads:

- Application tables need low-latency transactional reads and writes.
- Audit data is append-heavy and grows continuously.
- Audit searches are usually time-range, actor, entity, or event searches.
- Audit retention may be much longer than application-data retention.
- Audit reports can scan large historical ranges.
- Audit access should be more restricted than normal application access.
- Audit storage may need independent backup, retention, and compliance controls.

Keeping all audit rows on the application database creates pressure on WAL volume, storage, indexes, backups, vacuuming, connection pools, and reporting queries. A separate audit system isolates those concerns.

## Data Classification

Not every historical record should be moved out of PostgreSQL.

### Primary PostgreSQL: business source of truth

These records remain in the application database and are updated transactionally:

- Wallet balances.
- Wallet ledger entries.
- Offers, requests, listings, and their state transitions.
- Idempotency records required to protect business operations.
- Notification outbox records required for recovery.
- A small amount of event metadata needed for local reconciliation.

The wallet ledger is business data, not merely an audit log. It must remain available for balance reconciliation even if the audit system is unavailable.

### Separate audit store: historical event record

These events should be exported asynchronously:

- Authentication attempts and identity-provider events.
- OTP creation, failure, consumption, and lockout events.
- Device-token registration, invalidation, and ownership changes.
- File upload, activation, access, tombstone, and cleanup events.
- User, buyer-profile, seller-profile, address, and authorization changes.
- Administrative actions.
- Notification delivery and retry history.
- Operational security events and selected request metadata.

The audit store must not become the source of truth for balances, ownership, permissions, or workflow state.

## Proposed Architecture

```text
Client request
      |
      v
WebFlux controller -> application service
      |
      +--> update business tables in PostgreSQL
      +--> insert outbox event in the same PostgreSQL transaction
      |
      v
Transaction commits
      |
      v
Outbox claim worker
      |
      +--> bounded queue / Redis delivery projection
      |
      v
Audit publisher
      |
      +--> separate audit PostgreSQL or append-only audit store
      +--> retry / dead-letter / reconciliation
```

The outbox is the durable handoff boundary. Redis may accelerate delivery, but it must not be the only copy of an audit event that still needs delivery.

## Transaction and Delivery Semantics

The application transaction follows this order:

1. Validate the request and authorization.
2. Change the business records.
3. Insert a versioned audit event into the PostgreSQL outbox.
4. Commit both changes together.

After commit, an audit worker:

1. Claims pending outbox rows with a bounded lease.
2. Publishes the event to the audit store.
3. Marks the outbox event as dispatched only after the audit store acknowledges the write.
4. Retries transient failures with bounded exponential backoff.
5. Moves exhausted events to durable dead-letter state.
6. Replays authorized events by stable event ID when required.

Delivery is at-least-once. The audit store must enforce a unique event ID so retries do not create duplicate audit records.

The expected consistency model is:

- Business data: immediately consistent in the primary database.
- Audit data: durably committed to the outbox immediately, visible in the separate audit store eventually.
- Financial ledger: immediately consistent and queryable in the primary database.

An application crash after the business commit cannot lose the audit event because the outbox row is already durable.

## Event Contract

Each audit event should contain only the information needed for investigation and correlation:

```json
{
  "eventId": "stable UUID",
  "eventType": "USER_STATUS_CHANGED",
  "schemaVersion": 1,
  "occurredAt": "UTC timestamp",
  "actorPublicId": "public UUID or null for system action",
  "subjectType": "USER",
  "subjectPublicId": "public UUID",
  "correlationId": "request/correlation UUID",
  "source": "auto-parto-api",
  "metadata": {}
}
```

Rules:

- Use public UUIDs in exported audit events. Do not export internal `BIGINT` IDs as public identifiers.
- Do not include passwords, JWTs, OTP values, private keys, complete payment credentials, or unnecessary personal data.
- Store hashes, classifications, or redacted values where the original value is not needed.
- Include an event schema version and support controlled consumer migration.
- Make `eventId` immutable and unique in the audit store.
- Keep payload size bounded. Large request or response bodies must never be copied into audit events by default.

## Primary Outbox Design

The application database should retain a bounded operational outbox record with fields equivalent to:

- Internal row ID for claiming and joins.
- Stable public `event_id` UUID.
- Event type and schema version.
- Aggregate type and public aggregate ID.
- Correlation and trace IDs.
- Redacted event payload or a reference to the event data.
- Status: `PENDING`, `CLAIMED`, `DISPATCHED`, or `EXHAUSTED`.
- Availability time, lease owner, and lease expiry.
- Attempt count and sanitized last-error classification.
- Created and updated timestamps.

Outbox rows should be retained until successful delivery plus a bounded reconciliation window. After that, they may be archived or deleted according to the recovery policy.

## Audit Store Design

The separate audit system should provide:

- Append-only event records.
- Unique constraint on `event_id`.
- Time-based partitioning.
- Indexes for event time, subject, actor, event type, and correlation ID.
- Separate read and write credentials.
- Restricted network access.
- Independent backup and retention policies.
- Encryption in transit and at rest.
- Controlled administrative access and access logging.
- A bounded ingestion queue and dead-letter process.

An initial implementation may use a separate PostgreSQL database with the same provider. The stronger production target is a separate PostgreSQL instance or managed service so audit workload, failure, maintenance, and resource limits are isolated from the application database.

For very long retention, older partitions can be exported to encrypted object storage in an immutable format. The audit database remains the searchable hot-retention layer.

## Failure and Recovery Behavior

### Audit store unavailable

- Business requests continue if the primary transaction and outbox succeed.
- The publisher stops acknowledging successful delivery.
- Outbox rows remain pending or are reclaimed after lease expiry.
- Retry attempts are bounded and observable.
- Exhausted events remain in PostgreSQL dead-letter state.
- Recovery resumes from the oldest pending event.

### Redis unavailable

- Redis delivery projections are rebuilt from PostgreSQL outbox rows.
- The system must not discard audit events because a Redis queue or cache is unavailable.

### Duplicate delivery

- The audit store inserts by stable `event_id`.
- A duplicate is treated as an idempotent success.
- Metrics distinguish new writes from duplicate acknowledgements.

### Audit database data loss

- Restore from independent backups or immutable object-storage exports.
- Reconcile against retained PostgreSQL outbox rows and delivery metrics.
- Preserve an operational report of gaps and replay attempts.

### Primary database failure

- Application business recovery follows the PostgreSQL backup and restore policy.
- Outbox rows restored with the primary database are replayed safely.
- The audit store does not replace primary business-data recovery.

## Security and Privacy

The audit store must have narrower access than the application database:

- The application runtime can publish audit events but cannot delete audit history.
- Audit readers use a separate read-only role.
- Operational administrators cannot silently rewrite historical events.
- Deletion requires an explicit retention or privacy workflow with approval and logging.
- Audit payloads are redacted before leaving the application boundary.
- Event access is itself audited.

Audit data can contain sensitive personal and security information. Separation does not remove privacy obligations; it makes retention and access boundaries explicit.

## Migration From the Current AP-0001 Design

The current AP-0001 schema places `audit_records` and audit foreign keys in the primary PostgreSQL database. Before AP-0002 builds runtime repositories, decide whether those fields remain part of the target schema.

Recommended migration:

1. Keep `wallet_ledger` and its immutable financial history in primary PostgreSQL.
2. Keep the durable outbox in primary PostgreSQL.
3. Replace high-volume synchronous `audit_records` writes with versioned outbox audit events.
4. Replace ordinary cross-table audit foreign keys with stable event UUID references where local linkage is useful.
5. Remove large audit-history indexes from the application database.
6. Create the separate audit schema and unique event-ID constraint.
7. Backfill only deliberately selected historical records; do not import transient data blindly.
8. Run a dual-publish validation period if needed, with one authoritative event ID and reconciliation metrics.
9. Remove the old same-database audit path after delivery, replay, and recovery tests pass.

Do not implement two independent audit sources without a documented authority, event ID, retry policy, and reconciliation process.

## Operational Metrics and Alerts

Track at minimum:

- Oldest pending outbox event age.
- Pending, claimed, dispatched, and exhausted event counts.
- Audit publisher throughput and latency.
- Retry count and retry age.
- Duplicate event acknowledgements.
- Dead-letter count and oldest dead-letter age.
- Audit-store write failures and connection saturation.
- Reconciliation gaps.
- Audit storage growth and partition size.
- Backup age and restore-test results.

Alert on delivery lag, exhausted events, unbounded outbox growth, audit-store unavailability, reconciliation gaps, and failed backups.

## Phased Implementation Plan

### Phase 1: Contract and ownership

- Approve event categories and the primary-versus-audit-store boundary.
- Define redaction, retention, and access rules.
- Version the audit event contract.

### Phase 2: Primary outbox

- Add durable audit event rows to the PostgreSQL outbox.
- Add atomic claim and lease behavior.
- Add idempotency and bounded retry state.
- Add focused failure and duplicate-delivery tests.

### Phase 3: Separate audit store

- Provision an isolated PostgreSQL database or approved append-only store.
- Add restricted publisher and reader credentials.
- Add partitions, indexes, backups, and retention jobs.
- Add unique event-ID ingestion.

### Phase 4: Recovery and migration

- Add replay and reconciliation jobs.
- Test primary recovery, audit-store recovery, Redis loss, duplicate delivery, and publisher crash scenarios.
- Backfill selected existing history if required.
- Remove the old same-database high-volume audit path.

## Decision and Ticket Impact

- AP-0001 should own the durable outbox and business-data integrity boundaries, not the full long-term audit warehouse.
- AP-0002 should keep runtime access non-blocking and make audit publishing asynchronous.
- AP-0003 should define deployment ordering for the primary database, outbox worker, and audit database migration.
- Notification and security tickets should publish versioned audit events through the shared outbox contract.
- A future ticket should own audit-store provisioning, retention, replay, reconciliation, and recovery drills.

## Applied Foundation Status

The audit schema migration now establishes append-only triggers and provisions `audit_writer`/`audit_reader` capability roles. Production deployment must bind separate login credentials to those roles and must not use the migration owner as an application runtime identity.

The first four implementation steps now have a repository foundation:

- Expand-contract schema: `V2__separate_audit_delivery.sql` adds public aggregate identifiers and independent per-destination delivery state to the application outbox.
- Dedicated audit schema: `db/audit-migration/V1__create_audit_event_schema.sql` defines append-only, idempotent audit events and indexes.
- Publisher orchestration: the reactive `auto.parto.audit` port and publisher classes implement bounded claiming, at-least-once delivery, duplicate acknowledgement, retry, exhaustion, replay, and reconciliation behavior.
- Deployment resource: the opt-in Docker Compose `postgres`/`audit` profiles define separate application and audit PostgreSQL resources with separate credentials and volumes.

The PostgreSQL R2DBC outbox adapter, audit-database adapter, deployment migration jobs, and Docker-backed integration execution are wired into the PostgreSQL application runtime.

## Final Operating Rules

- The application PostgreSQL database and the audit PostgreSQL database are separate resources, not merely separate schemas on one instance.
- The application database owns business state, wallet balances, wallet ledger entries, and the durable outbox.
- The audit database owns exported historical audit events and has independent retention, indexing, backup, and access policies.
- Audit delivery is asynchronous and at-least-once. Stable event IDs make retries idempotent.
- Notification delivery and audit delivery have independent status, lease, retry, exhaustion, and reconciliation state.
- Security events without a business mutation use an independently committed audit-outbox transaction in the application database.
- Exported events contain public UUIDs, never internal numeric IDs.
- Redis is optional and rebuildable. It cannot be the only durable copy of an audit event.
- The application continues serving business requests when the audit database is unavailable, while bounded outbox backlog and delivery lag are monitored.
- Audit retention, redaction, legal holds, deletion approvals, and immutable archival are managed independently from application-data retention.

The current AP-0001 audit-table design must be migrated using an expand-contract plan before runtime repositories depend on it. The implementation must preserve wallet ledger integrity while moving high-volume audit history to the dedicated audit resource.

## Recommendation

Adopt the separated audit-store design with a transactional PostgreSQL outbox. Do not synchronously write the application transaction and a second database. Keep financial ledger data in the primary database, export high-volume historical audit events asynchronously, and make the audit store independently recoverable, access-controlled, and idempotent.
