# AP-0027 Harden SeaweedFS Operations

- **Status:** Done
- **Type:** Technical Enhancement
- **Feature:** [File Storage](../features/F06-file-storage.md)
- **Linked Tickets:** [AP-0017](AP-0017-storage-boundary.md), [AP-0019](AP-0019-storage-reconciliation.md), [AP-0022](AP-0022-openbao-secrets.md)
- **Source:** `deployment-architecture.md` SeaweedFS

## Description
Operate SeaweedFS with private TLS access, least-privilege IAM, encryption, replication, backups, and tested restoration.

## Acceptance Criteria
1. The S3 gateway is private or TLS-protected with persisted default-deny IAM policies.
2. Server-side encryption keys are supplied by the approved secrets system.
3. Volumes and filer metadata use documented persistence, replication, and failure-domain placement.
4. Off-cluster backups cover volume data, metadata, IAM state, keys, and deletion/version policy.
5. Restore order and volume-failure procedures are tested with reconciliation.

## Test Scenarios
1. **Given** an unauthorized identity, **when** it accesses the gateway, **then** access is denied.
2. **Given** a volume failure, **when** recovery runs, **then** required objects remain available or restore successfully.
3. **Given** a complete backup, **when** restored in order, **then** objects, metadata, policies, and access recover.

## Definition of Done
The whole ticket is implemented, every acceptance criterion is satisfied, every scenario is automated or executed as a repeatable recovery test and passes, runbooks are complete, and all applicable suites pass.

## Implementation Tracking

- The production storage contract, private/TLS gateway, default-deny IAM, secret-managed credentials, persistence and replication requirements, backup contents, and restore order are recorded in [`docs/file-storage.md`](../../docs/file-storage.md) and `docs/deployment-architecture.md`.
- The application reconciliation gate is the post-restore verification step: it is bounded, fails closed on missing active objects or scan overflow, and can apply an explicitly enabled orphan cleanup policy.
