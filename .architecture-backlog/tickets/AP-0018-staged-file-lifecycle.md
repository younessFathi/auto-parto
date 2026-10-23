# AP-0018 Implement Secure Staged File Lifecycle

- **Status:** Done
- **Type:** User Story
- **Feature:** [File Storage](../features/F06-file-storage.md)
- **Linked Tickets:** [AP-0005](AP-0005-prevent-identity-impersonation.md), [AP-0007](AP-0007-part-requests.md), [AP-0008](AP-0008-part-listings.md), [AP-0017](AP-0017-storage-boundary.md)
- **Source:** `architecture.md` File Storage; `deployment-architecture.md` File States

## Description
As a resource owner, I want securely validated uploads so unauthorized or invalid content never becomes active.

## Acceptance Criteria
1. Authentication and ownership authorization occur before metadata, destinations, or bytes are accepted.
2. Uploads enforce size, MIME, signature, dimensions, safe keys, checksum, and quota.
3. Metadata transitions through `PENDING`, `ACTIVE`, and `TOMBSTONED` only by guarded rules.
4. Finalization is idempotent and deletion is asynchronous.

## Test Scenarios
1. **Given** a non-owner, **when** upload starts, **then** `403` is returned and nothing is created.
2. **Given** invalid content, **when** finalized, **then** it never becomes `ACTIVE`.
3. **Given** repeated finalization, **when** invoked, **then** one active object results.

## Definition of Done
The whole ticket is implemented, every acceptance criterion is satisfied, every scenario is automated and passing, authorization and storage integration tests are complete, and all applicable suites pass.

## Implementation Tracking

- Started as part of feature F06 after the storage boundary was verified on `main`.
- The first lifecycle correction makes finalization idempotent: repeating activation of an already active file returns the existing owned active record without creating another transition or deleting its object.
- Uploads now apply bounded decoded image dimensions and an owner active-file quota before metadata creation. Provider cleanup remains bounded on `boundedElastic`, and failed publication tombstones metadata while deleting the object idempotently.
- Focused lifecycle, storage, repository, ownership, and full Maven verification suites pass.
- Review follow-up makes quota admission atomic under PostgreSQL owner locking, makes concurrent finalization return the winning active record, and rejects undecodable signature-prefixed payloads.
- Stale `PENDING` metadata is now recovered during bounded startup reconciliation using a configurable one-hour lease by default: the still-pending row is atomically tombstoned and its provider object is deleted idempotently, releasing the owner quota reservation after interruption.
