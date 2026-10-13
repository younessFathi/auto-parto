# AP-0002 / AP-0003 PostgreSQL Cutover Branch

This document explains the work delivered by `feat/ap-0002-ap-0003-postgresql-cutover`, the reason for each step, and the evidence used to verify the branch. The operational procedures remain in [postgresql-development-cutover.md](postgresql-development-cutover.md); this file is the implementation history and design guide for the branch.

## Goal

AP-0002 introduces reactive PostgreSQL persistence behind repository boundaries. AP-0003 uses those boundaries to provide a repeatable development cutover from MongoDB to PostgreSQL.

The branch was designed around four constraints:

1. Request paths stay reactive and do not call `block()` or `subscribe()` to force work to run.
2. PostgreSQL is the source of truth when a boundary is enabled; there is no dual write path.
3. Every query and queue is bounded, and ownership is derived from authenticated identity and database relationships.
4. A cutover stops or remains unready when migrations, fixtures, reconciliation, or storage checks fail.

## What was implemented

### 1. PostgreSQL persistence foundations

The branch completed the R2DBC configuration, pooled connections, reactive transactions, public UUID repository boundaries, and conditional feature flags. Internal PostgreSQL numeric IDs remain inside repositories, while services use public IDs.

This keeps SQL details out of services and allows each aggregate to move independently without exposing a second persistence contract.

### 2. Identity and profile paths

Buyer and seller identity, profile, provider-subject lookup, OTP replacement and validation, activation, and deactivation paths were routed to PostgreSQL when their flags are enabled. Buyer-only configuration also enables the shared R2DBC transaction infrastructure so it cannot expose partially configured beans.

Identity mutations use reactive transactions, bounded OTP attempts, hashed OTP values, and explicit empty results for missing users. The existing Mongo compatibility paths remain available only outside the explicit Mongo-disabled cutover.

### 3. Marketplace persistence

PostgreSQL repositories were added for part requests, part listings, and offers. They provide:

- canonical public UUID access;
- bounded owner-scoped reads;
- foreign-key target checks;
- explicit empty results for missing or inaccessible records;
- optimistic version checks for status changes; and
- transaction-safe creation.

`PieceService` routes canonical UUID request reads, existence checks, creation, and image updates through PostgreSQL. Offer creation and reads derive the counterparty from the target row instead of trusting a request-supplied user ID.

### 4. Wallet, device-token, and notification persistence

Wallet mutations use PostgreSQL row locking, `long` minor units, overflow-safe arithmetic, immutable ledger entries, explicit currency, and wallet-scoped idempotency keys. This protects retries and preserves an auditable money history.

Device tokens and durable notification records use PostgreSQL as the source of truth when enabled. Redis remains the delivery projection for notification work. Reads are recipient or owner scoped and bounded; status transitions and channel attempts are idempotent.

Notification preferences use transactional PostgreSQL upserts with canonical user ownership.

### 5. Canonical file persistence and object storage

Canonical request and listing image reads and uploads were moved to a provider-neutral `FileStorage` boundary. File metadata follows a pending-to-active lifecycle:

1. Validate the request and target ownership.
2. Write metadata as `PENDING`.
3. Stage and store the object.
4. Activate metadata transactionally.
5. Delete the staging file.

Failures delete the object where possible, remove staging data, and tombstone metadata. PostgreSQL inserts join the target row to the authenticated owner, so a valid target belonging to another user returns an empty result and a safe client error.

The local adapter is used for development and tests. Production requires the asynchronous S3-compatible adapter and protected credentials.

### 6. Upload safety

Canonical image staging now has a configured maximum, defaulting to 10 MiB through `APP_FILE_STORAGE_MAX_UPLOAD_BYTES`. Before any file is staged, the service verifies:

- the extension is allowed;
- the declared MIME type matches the extension; and
- the bytes have the expected image signature.

This prevents arbitrary content with an image filename from reaching local or object storage and bounds the in-memory aggregation used for validation.

### 7. PostgreSQL-only cutover profile

The Compose PostgreSQL profile was completed so the application can run without MongoDB auto-configuration. It enables the PostgreSQL boundaries, waits for Flyway migrations and deterministic fixtures, disables external Firebase dependencies for local use, and exposes an unauthenticated actuator health endpoint.

The cutover context suppresses legacy Mongo repository beans and fails closed on unsupported compatibility contracts. This makes accidental Mongo writes visible during PostgreSQL-only development.

### 8. Migrations, roles, and fixtures

The deployment path uses separate bootstrap, restricted runtime, and migration roles. Flyway runs as a one-shot migration job before fixtures and application startup. The fixture job uses the restricted runtime role, is transactional and idempotent, and fails closed on conflicting deterministic data.

The branch intentionally does not import transient OTP, notification, outbox, or file-object state. Development data is recreated through PostgreSQL fixtures instead of copying undocumented local state.

### 9. Reconciliation and readiness

SQL reconciliation verifies deterministic identities, marketplace records, device tokens, and wallet balance-to-ledger equality. It also enforces the selected no-file-import policy.

The object-storage reconciliation service counts active file metadata before running its bounded existence scan. If the count exceeds the configured capacity, it fails closed instead of silently skipping later records. Missing objects and reconciliation errors also remain unhealthy.

The startup gate is asynchronous. The process may bind while reconciliation is running, but its reactive health contribution remains down until reconciliation succeeds. Deployment and load-balancer readiness must use `/actuator/health`; traffic must not be sent while that endpoint is down.

### 10. Review remediation

The first cutover review identified four valid issues. They were addressed as follows:

| Finding | Resolution |
|---|---|
| Active file records beyond the query limit could be skipped | Count active records first and fail closed when the configured capacity is exceeded. |
| Uploads accepted unbounded or arbitrary content | Add bounded aggregation, MIME/extension agreement, and signature validation. |
| File inserts did not enforce target ownership | Join request/listing targets to their owner in the insert query. |
| Startup used a blocking `ApplicationRunner` | Replace it with an asynchronous reactive health gate. |

The follow-up review found one documentation issue: the runbook still described blocking startup. Commit `95c3d58` corrected the wording and documented `/actuator/health` as the readiness signal.

## Feature flags

The main cutover flags are:

| Flag | Purpose |
|---|---|
| `APP_POSTGRES_CUTOVER_ENABLED` | Enables explicit PostgreSQL cutover behavior. |
| `APP_POSTGRES_MONGO_DISABLED` | Suppresses MongoDB compatibility repositories. |
| `APP_POSTGRES_CUTOVER_API_CONTRACTS_READY` | Confirms the selected API contracts are ready for cutover. |
| `APP_POSTGRES_CUTOVER_STORAGE_RECONCILIATION_ENABLED` | Enables the storage reconciliation health gate. |
| `APP_POSTGRES_MARKETPLACE_ENABLED` | Enables request, listing, and offer PostgreSQL paths. |
| `APP_POSTGRES_WALLET_ENABLED` | Enables PostgreSQL wallet and ledger mutations. |
| `APP_POSTGRES_DEVICE_TOKENS_ENABLED` | Enables PostgreSQL device-token persistence. |
| `APP_POSTGRES_NOTIFICATIONS_ENABLED` | Enables PostgreSQL durable notification records. |
| `APP_POSTGRES_FILES_ENABLED` | Enables PostgreSQL file metadata and canonical upload paths. |
| `APP_FILE_STORAGE_MAX_UPLOAD_BYTES` | Sets the bounded image upload size. |

## Verification evidence

The branch was verified with:

- `./mvnw -B test`: 301 tests passed, 1 skipped.
- `./mvnw -B verify`: 113 Docker/Testcontainers integration tests passed, 1 skipped, and JaCoCo checks passed.
- `./deploy/postgres/compose-smoke-test.sh`: migrations, fixture idempotency, SQL reconciliation, and invalid-target failure-stop all passed with ephemeral review credentials.
- `git diff --check`: passed.
- Focused upload, reconciliation, readiness, ownership, Compose, and migration tests passed.

The PostgreSQL application profile was also started previously with PostgreSQL and Redis health components reporting up after the cutover checks completed.

## Commit sequence

The branch was built in small reviewable increments:

1. `9fd39a2` — establish PostgreSQL persistence foundations.
2. `3d4a291` — cover the reconciliation wrapper.
3. `ee571b9` — route canonical file reads to PostgreSQL.
4. `5c49b6b` — persist request images in PostgreSQL.
5. `b70dc54` — persist listing images in PostgreSQL.
6. `b1fbc0d` — route offer traffic to PostgreSQL.
7. `3d0debe` — return not found for inaccessible offers.
8. `101b511` — reconcile PostgreSQL cutover status documentation.
9. `3706c5e` — add the durable provider-neutral file storage boundary.
10. `1fd7c14` — route canonical file batches to PostgreSQL.
11. `5ee4320` — finish canonical PostgreSQL request boundaries.
12. `fff8741` — enforce the Mongo-disabled PostgreSQL cutover.
13. `938033f` — add the PostgreSQL storage reconciliation gate.
14. `36cd92a` — route identity service methods to PostgreSQL.
15. `cd53b0f` — complete the combined AP-0002/AP-0003 cutover scope.
16. `5989cfe` — complete the PostgreSQL cutover implementation.
17. `0c7001a` — verify the real Compose smoke path.
18. `2984edc` — align the cutover fixture policy documentation.
19. `de33059` — address the four valid cutover review findings.
20. `95c3d58` — clarify asynchronous readiness in the operational runbook.

## Current boundary and follow-up

AP-0002 and AP-0003 are complete for the development cutover scope. Production rollout still requires the documented protected secrets, backup and recovery procedures, production object-storage configuration, and operational readiness checks described in the deployment runbook.
