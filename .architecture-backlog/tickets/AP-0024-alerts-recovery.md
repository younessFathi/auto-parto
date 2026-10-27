# AP-0024 Establish Alerts and Recovery Drills

- **Status:** Blocked
- **Type:** Technical Enhancement
- **Feature:** [Observability and Recovery](../features/F08-observability-and-recovery.md)
- **Linked Tickets:** [AP-0013](AP-0013-outbox-processing.md), [AP-0019](AP-0019-storage-reconciliation.md), [AP-0021](AP-0021-tls-entrypoint.md), [AP-0022](AP-0022-openbao-secrets.md), [AP-0023](AP-0023-telemetry-health.md), [AP-0026](AP-0026-data-infrastructure.md)
- **Source:** `deployment-architecture.md` Alerting and Recovery Requirements

## Description
Create owned alerts and exercise restoration, replay, rotation, rollback, and reconciliation against measurable objectives.

## Acceptance Criteria
1. Critical HTTP, database, Redis, notification, SSE, storage, and heartbeat failures have an owner, severity, recovery objective, and runbook.
2. PostgreSQL meets approved RPO/RTO using encrypted off-host backups and point-in-time recovery.
3. Restore acceptance reconciles wallet balances against immutable ledger entries.
4. Drills cover Redis projection rebuild, SeaweedFS restore, secret rotation, application rollback, and duplicate notifications.
5. Drill outcomes and unresolved gaps are recorded and actionable.
6. Incident communication, escalation, and ownership are documented and exercised.

## Test Scenarios
1. **Given** an isolated environment, **when** PostgreSQL PITR runs, **then** RPO/RTO and wallet reconciliation pass.
2. **Given** Redis loss, **when** recovery runs, **then** projections rebuild without losing authoritative work.
3. **Given** a failed rollout, **when** rollback runs, **then** the previous compatible version is restored.
4. **Given** a critical alert, **when** the incident process starts, **then** severity, owner, communication, and recovery objective are available.

## Definition of Done
The whole ticket is implemented, every acceptance criterion is satisfied, every scenario is automated or executed as a repeatable recovery test and passes, runbooks are complete, and all applicable suites pass.

## Dependency Gate

Blocked pending demonstrable completion of AP-0021 (TLS/network boundary), AP-0022 (deployed OpenBao and recovery/rotation evidence), AP-0023 (deployed protected observability), and AP-0026 (database/Redis infrastructure and backup controls). These tickets are currently Proposed or In Progress. No controlled staging environment is available in this change, so no recovery or alert drills are claimed. Once unblocked, record separate encrypted off-host application/audit PostgreSQL PITR restore measurements, wallet-ledger reconciliation, Redis rebuild, SeaweedFS restore, secret rotation/old-key rejection, application rollback, duplicate notification recovery, and alert escalation evidence with named owners.
