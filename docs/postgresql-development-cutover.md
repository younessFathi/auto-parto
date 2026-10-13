# PostgreSQL Development Migration and Cutover

## Current boundary

This is an incremental development migration, not yet an application cutover. PostgreSQL currently hosts the versioned target schema, the AP-0002 identity/profile and audit-outbox adapters, and deterministic development fixtures for the migrated identity/profile boundary. The remaining business repositories still use reactive MongoDB. Do not remove MongoDB dependencies, import local MongoDB data, or direct all business traffic to PostgreSQL until the remaining repository, fixture, and reconciliation gates pass.

The first marketplace persistence boundaries are introduced behind `APP_POSTGRES_MARKETPLACE_ENABLED`. They cover PostgreSQL part-request, part-listing, and offer reads and mutations with canonical public UUIDs, bounded owner pagination, target constraints, and optimistic version checks. `PieceService` routes canonical UUID request reads and creation through this boundary. Buyer identity checks and deactivation, seller identity reads/deactivation, and seller wallet mutations also route through PostgreSQL when their boundaries are enabled; wallet mutations require an `Idempotency-Key` and explicit currency. Authenticated offer creation, actor-scoped reads, and status updates derive ownership from the PostgreSQL target row and return 404 for missing or inaccessible offers. The device-token boundary is introduced behind `APP_POSTGRES_DEVICE_TOKENS_ENABLED`; PostgreSQL owns active token registration and invalidation while Redis remains the notification delivery projection. The notification record boundary is introduced behind `APP_POSTGRES_NOTIFICATIONS_ENABLED` for durable pending event records, recipient-scoped reads, and transactional pending-to-dispatched transitions. Canonical UUID request-image reads, authenticated buyer request-image uploads, and authenticated seller listing-image uploads route through the PostgreSQL file boundary when `APP_POSTGRES_FILES_ENABLED` is enabled; image relationship writes and object retrieval remain open because their storage contract is not yet defined.

No local-data importer is selected for this development cutover. Local development data should be recreated using PostgreSQL-backed fixtures after the corresponding repositories are migrated. Transient OTP, device-token, notification, and outbox state is not imported by default.

## Object storage boundary

PostgreSQL stores file metadata and object keys only. PostgreSQL-backed uploads create metadata in `PENDING`, write the object through the provider-neutral `FileStorage` port, and then activate the metadata transactionally; failed writes clean up the object and tombstone metadata when it was created. Canonical file reads resolve metadata by public UUID and stream bytes through the same port.

Use `APP_FILE_STORAGE_PROVIDER=local` for local development and tests. The local adapter uses bounded filesystem work and is rejected when the `prod` Spring profile is active. Production must set `APP_FILE_STORAGE_PROVIDER=s3` and provide `APP_FILE_STORAGE_S3_ENDPOINT`, `APP_FILE_STORAGE_S3_BUCKET`, `APP_FILE_STORAGE_S3_REGION`, `APP_FILE_STORAGE_S3_ACCESS_KEY`, and `APP_FILE_STORAGE_S3_SECRET_KEY`; the adapter uses the asynchronous S3 client and path-style addressing for SeaweedFS's S3 gateway. Keep credentials in the approved secrets system.

## Local PostgreSQL schema migration

Supply these variables through a local ignored `.env` file or the shell. Use distinct generated values; never commit them:

- `APP_POSTGRES_DB` (optional; defaults to `auto_parto`)
- `APP_POSTGRES_BOOTSTRAP_PASSWORD`
- `APP_POSTGRES_RUNTIME_PASSWORD`
- `APP_POSTGRES_MIGRATION_USER`
- `APP_POSTGRES_MIGRATION_PASSWORD`
- `APP_POSTGRES_RUNTIME_USER` (optional; defaults to `auto_parto_runtime`)

The bootstrap account is used only during first initialization. The initialization SQL creates non-superuser runtime and migration roles. Runtime receives DML and sequence access on migration-created objects; the migration role receives schema creation privileges. Flyway connects only with the migration role.

The PostgreSQL image runs initialization scripts only when `PGDATA` is empty. On an existing `app-postgres-data` volume, changing these variables or mounting the init script does not provision the new roles. Do not delete the volume to force initialization. Have an authorized database operator provision the runtime and migration roles and grants on that existing database, then verify them before running Flyway; the migration job should fail closed if the migration role is absent.

Start the database and wait for its health check:

```bash
docker compose --profile postgres up -d app-postgresql
```

Run the one-shot migration and require its exit status to be zero before proceeding:

```bash
docker compose --profile postgres run --rm app-postgresql-migrate
```

Flyway records applied versions in `flyway_schema_history`, so rerunning this command is safe and applies only pending migrations. Migrations are mounted read-only from `src/main/resources/db/migration`. The service has no restart policy; a nonzero result is a hard stop, not permission to continue with an application rollout.

After migration succeeds, run the development identity/profile fixtures:

```bash
docker compose --profile postgres run --rm app-postgresql-fixtures
```

The fixture service is structurally ordered after the Flyway service and connects with the restricted runtime role, not the bootstrap or migration role. It creates deterministic development buyer and seller identities, profiles, marketplace records, a wallet/ledger pair, and one active device token. The script is transactional and idempotent: rerunning it does not duplicate users, profiles, marketplace records, wallet entries, or device tokens. If a deterministic fixture conflicts with existing data, the script exits nonzero and rolls back the run. It intentionally does not seed OTP challenges, files, notifications, or outbox state because those boundaries are either transient or their API/object-storage contracts are not yet migrated from MongoDB.

For an existing PostgreSQL volume, an authorized operator must provision and verify the roles instead of relying on the init hook. Set the protected `PGHOST`, `PGDATABASE`, `PGUSER`, and `PGPASSWORD` connection variables together with the four `APP_POSTGRES_*` role variables, review the output, and run:

```bash
./deploy/postgres/operations/provision-existing-volume.sh
```

The procedure creates or updates only the restricted runtime and migration roles, repairs runtime DML grants on existing tables and `USAGE`/`SELECT` grants on existing sequences in the approved `public` application schema, and grants the migration role DML access to the existing Flyway history table. It prints role attributes, per-object ownership, runtime privileges, default privileges, and Flyway-history access for review. It does not drop or delete roles, tables, schemas, sequences, volumes, or Flyway history. It never performs global ownership reassignment.

The procedure has two possible outcomes:

1. If every existing public application table and sequence is owned by the migration role, it verifies the runtime grants and connects as the migration role to update Flyway history inside a rollback-only transaction, create/alter/drop a temporary future-migration probe, and exit successfully.
2. If any existing public application table or sequence other than `flyway_schema_history` is owned by another role, it prints the exact per-object `ALTER TABLE ... OWNER TO ...` or `ALTER SEQUENCE ... OWNER TO ...` commands and exits nonzero. Runtime grants already repaired in that invocation are retained, but rollout must stop. An authorized DBA must review each listed object, execute only the approved per-object ownership commands, and rerun the procedure. Do not use `REASSIGN OWNED`, wildcard ownership changes, or volume deletion.

The Flyway history table is handled separately: its existing owner is reported, the migration role receives only the required history-table DML grants, and the migration-role verification proves read/update access and the ability to apply DDL in a rollback-only probe. If that verification fails, fix the exact reported role, schema, or history-table privilege issue and rerun the procedure; do not hand-edit `flyway_schema_history`.

To check migration state, run Flyway `info` with the same migration-role settings before retrying a failure. Do not run `flyway clean`, delete the PostgreSQL volume, or hand-edit Flyway history as a recovery shortcut.

## Deployment ordering and failure handling

For any future PostgreSQL-backed application deployment:

1. Provision/back up the target database and migration credentials.
2. Run the restricted JDBC migration job and require success.
3. Run the PostgreSQL fixtures for every migrated repository and reconciliation checks against that exact database.
4. Roll out application code only after both steps pass.
5. Enable the PostgreSQL runtime feature only for a build whose required repositories and health checks are PostgreSQL-ready.

The current repository has no production deployment job. When `APP_POSTGRES_CUTOVER_GATE=true` is set in a protected deployment pipeline, the `postgresql-migration-gate` job runs the restricted Flyway migration and the dependent `postgresql-reconciliation-gate` job applies fixtures with the runtime role and runs `deploy/postgres/operations/reconcile-development-fixtures.sh`. A failed migration, fixture conflict, or reconciliation mismatch prevents the package/deploy stages from proceeding. Application startup also refuses `APP_POSTGRES_CUTOVER_ENABLED=true` unless every API contract has been verified and `APP_POSTGRES_MONGO_DISABLED=true` explicitly confirms that MongoDB has been removed from the selected deployment.

If migration fails, stop rollout and preserve the database and logs. Fix the migration or use a new forward migration. Application rollback is safe only when the prior application version remains compatible with the expanded schema. Prefer forward-fix for an applied migration; restore a verified backup only under the approved recovery procedure when data integrity requires it. Never downgrade destructively or delete development/production volumes to make a migration pass.

The actual Flyway container path can be smoke-tested locally with the same restricted variables:

```bash
./deploy/postgres/compose-smoke-test.sh
```

This opt-in check starts the Compose PostgreSQL service, runs the real `flyway/flyway:10.20.1` service, applies the identity/profile fixtures twice to prove idempotency through the real runtime role, and then confirms that an invalid database target returns nonzero. It uses an isolated Compose project and removes only that project during cleanup.

## Cutover gates still open

- Migrate the remaining production business repositories behind their existing service boundaries and add PostgreSQL fixtures only as each boundary becomes authoritative. Identity/profile, marketplace request persistence, seller identity reads and wallet mutations, device-token, notification records, notification attempts, file metadata, notification preferences, authenticated request/listing image uploads, canonical object reads, and offer API ownership wiring are implemented as boundaries; legacy image relationship writes, storage reconciliation, and final PostgreSQL-only traffic verification still require cutover work.
- Decide explicitly whether any local data needs import. If so, implement an idempotent importer and identity, wallet, marketplace, and file reconciliation before enabling cutover.
- Block cutover when any reconciliation error remains.
- Verify PostgreSQL-backed application behavior and rollback compatibility before removing MongoDB dependencies or changing the primary database.

The reconciliation gate is intentionally fail-closed. It verifies deterministic identities, marketplace records, device tokens, and wallet totals against the immutable ledger inside a rollback-only transaction. It also rejects unexpected PostgreSQL file metadata under the selected no-file-import policy. File/object-storage comparisons and a durable object retrieval boundary are still required before the file subsystem can be considered fully cut over; the current fixture policy deliberately recreates no files.
