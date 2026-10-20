# AP-0019 Reconcile Storage and Metadata

- **Status:** Done
- **Type:** Technical Enhancement
- **Feature:** [File Storage](../features/F06-file-storage.md)
- **Linked Tickets:** [AP-0018](AP-0018-staged-file-lifecycle.md)
- **Source:** `deployment-architecture.md` File Storage and Recovery

## Description
Detect and safely remediate orphaned objects, missing objects, failed finalization, and post-restore inconsistency.

## Acceptance Criteria
1. Reconciliation scans are bounded, restart-safe, and observable.
2. Orphaned objects and active metadata without objects follow explicit remediation policies.
3. Repeated reconciliation is idempotent.
4. SeaweedFS recovery covers data, filer metadata, IAM state, encryption keys, and restore order.

## Test Scenarios
1. **Given** an object without metadata, **when** reconciliation runs, **then** policy is applied once.
2. **Given** active metadata without an object, **when** scanned, **then** the inconsistency is recorded and remediated.
3. **Given** restored storage, **when** reconciliation completes, **then** remaining drift is reported.

## Definition of Done
The whole ticket is implemented, every acceptance criterion is satisfied, every scenario is automated and passing, recovery runbooks and metrics are complete, and all applicable suites pass.

## Implementation Tracking

- Reconciliation now performs bounded active-metadata and provider-key scans, reports missing objects and orphan keys, and supports an explicit report-only default or bounded sequential orphan deletion policy.
- Missing objects keep the readiness gate unhealthy; repeated reconciliation is safe because provider deletion is idempotent and scans are stateless and bounded.
- The operational recovery procedure, restore order, and failure-domain requirements are documented in [`docs/file-storage.md`](../../docs/file-storage.md).
- Provider scans fetch a bounded sentinel and fail closed on overflow; partial scans cannot trigger orphan deletion. Readiness exposes bounded orphan details and the remediation mode.
- Reconciliation also recovers stale `PENDING` reservations using `APP_POSTGRES_CUTOVER_STORAGE_RECONCILIATION_PENDING_TIMEOUT` (default `1h`) and the same bounded scan limit. Recovery tombstones metadata before idempotently deleting its provider object and reports capacity overflow when another bounded run is required.
- Migration V11 adds `files_pending_recovery_idx` on `(created_at, public_id) WHERE status = 'PENDING'`; V10 is reserved for the device-token recovery index from main. This keeps stale reservation recovery index-backed as file metadata grows without colliding with the existing migration history.
- Local filesystem reconciliation orders keys deterministically while retaining only the bounded result window, so platform-specific directory traversal order cannot change which objects are inspected.
