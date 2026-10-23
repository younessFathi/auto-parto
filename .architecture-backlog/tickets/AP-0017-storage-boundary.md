# AP-0017 Establish S3-Compatible Storage Boundary

- **Status:** Done
- **Type:** Technical Enhancement
- **Feature:** [File Storage](../features/F06-file-storage.md)
- **Linked Tickets:** [AP-0002](AP-0002-reactive-persistence.md)
- **Source:** `architecture.md` File Storage; `deployment-architecture.md` SeaweedFS

## Description
Introduce a provider-neutral storage port that keeps object bytes outside PostgreSQL and supports local and SeaweedFS adapters.

## Acceptance Criteria
1. Application services depend on an internal storage contract, not a provider SDK.
2. PostgreSQL stores metadata and object keys, never uploaded bytes.
3. Local/test and SeaweedFS adapters satisfy the same contract.
4. Production cannot use a local container filesystem as durable upload storage.

## Test Scenarios
1. **Given** an upload, **when** metadata is inspected, **then** it contains an object key without bytes.
2. **Given** either adapter, **when** contract tests run, **then** behavior is equivalent.
3. **Given** production configuration with local storage, **when** startup runs, **then** it fails safely.

## Definition of Done
The whole ticket is implemented, every acceptance criterion is satisfied, every scenario is automated and passing, adapter documentation is complete, and all applicable suites pass.

## Implementation Tracking

- Completed on the PostgreSQL cutover line: application services depend on `FileStorage`, with local/test and asynchronous S3-compatible adapters.
- PostgreSQL stores file metadata and object keys only; production profile validation rejects local filesystem storage.
- Focused storage, upload, ownership, reconciliation, and migration tests provide the implementation evidence.
