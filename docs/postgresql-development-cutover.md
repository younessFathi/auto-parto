# PostgreSQL Development Migration and Cutover

## Current boundary

This is the completed PostgreSQL runtime path. PostgreSQL hosts the versioned target schema and the active identity/profile, marketplace, wallet, device-token, notification, file-metadata, notification-preferences, and object-storage boundaries.

The marketplace persistence boundaries are behind `APP_POSTGRES_MARKETPLACE_ENABLED`. They cover PostgreSQL part-request, part-listing, and offer reads and mutations with canonical public UUIDs, bounded owner pagination, target constraints, and optimistic version checks. `PieceService` routes canonical UUID request reads, creation, and image updates through this boundary; image ownership is established by the PostgreSQL file upload transaction and the update path rejects legacy image IDs. Buyer identity checks, provider-subject and phone/email lookup, OTP replacement/failure tracking, activation, and deactivation, plus seller identity reads, OTP replacement/failure tracking, activation/deactivation, and wallet mutations, route through PostgreSQL when their boundaries are enabled; wallet mutations require an `Idempotency-Key` and explicit currency. Authenticated offer creation, actor-scoped reads, and status updates derive ownership from the PostgreSQL target row and return 404 for missing or inaccessible offers. PostgreSQL owns active device-token registration and invalidation while Redis remains the notification delivery projection. The notification record boundary owns durable pending event records, recipient-scoped reads, and transactional pending-to-dispatched transitions. Canonical UUID request-image reads, authenticated buyer request-image uploads, and authenticated seller listing-image uploads route through the PostgreSQL file boundary when `APP_POSTGRES_FILES_ENABLED` is enabled. When cutover is enabled, the storage reconciliation startup gate runs asynchronously after the application starts and keeps the application's health status down until active PostgreSQL file records match the configured object provider. A missing object or reconciliation timeout therefore keeps the deployment unready.

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

Run the application with the completed PostgreSQL development profile. It enables every PostgreSQL boundary and waits for migration and fixtures. The base deployment keeps the application port internal so replicas can share a network; add the local development override to publish it on host port `8081`:

```bash
docker compose -f docker-compose.yml -f docker-compose.local.yml --profile postgres up -d springboot-postgresql-app
```

The application service uses PostgreSQL and Redis only. The production deployment should place a TLS entry point on the shared Compose network and scale the application service without publishing an instance-specific host port.

The fixture service is structurally ordered after the Flyway service and connects with the restricted runtime role, not the bootstrap or migration role. It creates deterministic development buyer and seller identities, profiles, marketplace records, a wallet/ledger pair, and one active device token. The script is transactional and idempotent: rerunning it does not duplicate users, profiles, marketplace records, wallet entries, or device tokens. If a deterministic fixture conflicts with existing data, the script exits nonzero and rolls back the run. It intentionally does not seed OTP challenges, files, notifications, or outbox state because those records are transient or require a separate validated object-storage/notification fixture manifest.

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
5. Set `APP_POSTGRES_CUTOVER_ENABLED=true`, `APP_POSTGRES_CUTOVER_API_CONTRACTS_READY=true`, and `APP_POSTGRES_CUTOVER_STORAGE_RECONCILIATION_ENABLED=true`; the application may bind while reconciliation runs, but `/actuator/health` remains down until the active file manifest check succeeds. Configure the deployment or load balancer readiness probe to use this unauthenticated endpoint and do not send traffic while it is down.
6. Enable the PostgreSQL runtime feature only for a build whose required repositories and health checks are PostgreSQL-ready.

The repository has no production deployment job. GitHub Actions exposes a manually dispatched, main-only cutover gate after both unit and integration suites pass. The gate targets a repository-level runner registered with the `ap-0033-private-cutover` label; no organization runner group is required. Register a dedicated Linux runner under this repository's Settings → Actions → Runners, use GitHub's `--ephemeral` registration mode so each job consumes and removes the runner, and place its host inside the private deployment network with only the required database egress. This runner must not be selected by pull-request or other untrusted workflows; the cutover job is limited to manual dispatch from `main` and has a protected environment. Also create the `postgresql-cutover` GitHub Environment, restrict deployments to `main`, require an authorized reviewer, and disable self-review where available. Store all migration, runtime database, backup attestation, cutover readiness, and enabled S3 credentials as environment secrets in that environment; do not use repository-wide secrets for these values. Keep the gate unavailable until the private runner and environment protection are configured; do not expose PostgreSQL publicly to make the gate connect. The job validates required database values and the verified-backup/cutover preflight before it runs Flyway, applies fixtures, performs reconciliation, and repeats preflight in that order. The test/package jobs do not depend on the cutover environment, and no application package is uploaded as an artifact. A failed check stops the sequence. This gate does not create a backup: an operator must take and verify the protected backup first, then record its timestamp and reference in the environment secrets.

If migration fails, stop rollout and preserve the database and logs. Fix the migration or use a new forward migration. Application rollback is safe only when the prior application version remains compatible with the expanded schema. Prefer forward-fix for an applied migration; restore a verified backup only under the approved recovery procedure when data integrity requires it. Never downgrade destructively or delete development/production volumes to make a migration pass.

The actual Flyway container path can be smoke-tested locally with the same restricted variables:

```bash
./deploy/postgres/compose-smoke-test.sh
```

This opt-in check starts the Compose PostgreSQL service, runs the real `flyway/flyway:10.20.1` service, applies the identity/profile fixtures twice to prove idempotency through the real runtime role, and then confirms that an invalid database target returns nonzero. It uses an isolated Compose project and removes only that project during cleanup.

## Completed cutover gates

- All active durable business boundaries use PostgreSQL, including provider-subject identity lookup and OTP/profile operations.
- Development uses a deliberate no-import policy. Durable fixtures are recreated in PostgreSQL; transient OTP, notification, outbox, and file-object state is not imported implicitly.
- SQL reconciliation and the bounded startup storage gate block cutover on fixture, wallet-ledger, unexpected-file-metadata, or missing-object failures.
- The protected manual migration gate validates the backup/environment attestation before migration, then runs migration, fixtures, reconciliation, and final preflight in dependency order. Expand-contract migration and forward-fix rollback procedures are documented above.

The reconciliation gate is intentionally fail-closed. It verifies deterministic identities, marketplace records, device tokens, and wallet totals against the immutable ledger inside a rollback-only transaction. It also rejects unexpected PostgreSQL file metadata under the selected no-file-import policy. The application startup gate verifies the provider/object manifest for active PostgreSQL file metadata with a bounded query and sequential existence checks. It is asynchronous: the process can accept connections while the gate is pending, but `/actuator/health` stays down and must be used as the deployment readiness signal until the check succeeds. The current fixture policy deliberately recreates no files, so an empty active-file set is valid until a file fixture/import policy is selected.
