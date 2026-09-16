# Architecture Design Review

## Scope

This review covers only:

- `docs/architecture.md`
- `docs/deployment-architecture.md`

The project is in development and has no production system or production data. This is a design review, not an implementation audit.

## Outcome

**Recommendation: approve the design direction conditionally, subject to implementation verification.**

The upload-authorization ordering inconsistency has been corrected in the deployment architecture. The remaining work is implementation and testing.

## Finding Addressed in This Pass

### Medium: Deployment upload workflow authorizes ownership too late

`architecture.md:228` correctly requires authentication and request/listing ownership authorization before creating upload metadata, issuing an upload destination, or accepting bytes.

`deployment-architecture.md` now specifies the same secure order:

1. Authenticate the caller.
2. Authorize ownership of the target request or listing.
3. Create `PENDING` metadata.
4. Issue the staged upload destination or accept bytes.
5. Validate content and object integrity.
6. Activate metadata.

The previous ordering permitted metadata creation and storage consumption before ownership confirmation. That contradiction is now removed.

Resolution in `deployment-architecture.md`:

- Ownership authorization occurs before metadata creation, upload destination issuance, and byte acceptance.
- Post-upload checks validate content and object integrity.
- Finalization is idempotent and failures become tombstones for asynchronous cleanup.

Ownership authorization must occur before metadata creation and byte acceptance. Post-upload validation should validate file and object integrity rather than establish whether the caller was allowed to upload.

## Closed Gates

### Durable notification persistence

- Business changes and outbox events are persisted in one PostgreSQL transaction.
- Redis is a rebuildable projection or fan-out mechanism.
- Enqueue and delivery occur only after durable persistence.

### Identifier contracts

- Public resource IDs and event IDs use UUIDs with explicit uniqueness rules.
- Trace and correlation IDs are observability context rather than business keys.
- Idempotency keys are caller-supplied, owner/operation scoped, fingerprinted, and retained.
- Conflicting idempotency-key reuse returns `409`.

### Device-token durability

- PostgreSQL is authoritative for device-token records.
- Redis contains rebuildable token indexes only.
- Registration is idempotent and enforces ownership and uniqueness.
- Invalid tokens are deactivated in PostgreSQL before Redis projections are updated.

## Previously Resolved Findings

- The domain separates buyer-owned `part_requests` from seller-owned `part_listings`.
- The development transition uses clean PostgreSQL replacement with optional one-time local-data import.
- Outbox claiming, leases, retries, duplicates, exhaustion, and dead-letter behavior are documented.
- Multi-instance SSE requires shared fan-out; sticky routing is only an optimization.
- SSE and FCM transport states are separated from durable event state.
- File storage uses staged keys and `PENDING`, `ACTIVE`, and `TOMBSTONED` states.
- Money uses integer minor units with currency and explicit precision rules.
- Runtime R2DBC access is separated from the JDBC migration job.
- Blocking provider SDKs require bounded isolation.
- Endpoint-specific rate-limit failure behavior is documented.
- PostgreSQL and SeaweedFS recovery requirements are documented.
- Health and observability boundaries are documented.

## Approval Gate

The design is ready for implementation-ticket generation. No new architecture correction is required from this review pass. Before production approval, verify the documented behavior through PostgreSQL schema review, authorization tests, notification/outbox tests, upload lifecycle tests, device-token persistence tests, and recovery drills.

## Verification

- Reviewed the latest working versions of `docs/architecture.md` and `docs/deployment-architecture.md`.
- No Java files, tests, current-risk documents, or existing deployment configuration were used as evidence.
- The latest working versions of the architecture documents were reviewed; the upload authorization order is consistent across both documents.
- No application tests were run because this was a documentation design review.
