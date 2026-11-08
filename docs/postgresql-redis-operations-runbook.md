# PostgreSQL and Redis Operations Runbook

This runbook describes the AP-0026 operating baseline. It is not proof of a Contabo deployment. Do not treat the production network, backup target, capacity, or recovery objectives as accepted until they are verified in staging.

## Network and transport boundaries

- Keep application PostgreSQL, audit PostgreSQL, and Redis on their assigned private interfaces. Firewall rules allow only the application host (and the separately controlled migration runner for PostgreSQL migration traffic) to connect. Never add a public load-balancer route or public bind for ports 5432 or 6379.
- PostgreSQL application and audit clients must use `sslMode=VERIFY_FULL`. The connection hostname must match the server certificate, and the configured trust store must contain the issuing CA.
- The production Redis client uses the named `redis` PEM SSL bundle. Set `REDIS_TLS_CA_HOST_PATH` to the operator-provisioned host file in the production Compose environment; Compose mounts it read-only at `/run/auto-parto/redis-ca.pem`, where `APP_REDIS_TLS_CA_FILE` points. The client loads that CA and enforces full certificate and hostname verification. Keep the CA rotation overlap in place until every app instance trusts the replacement certificate.
- The base local Compose network publishes no database/cache port. `docker-compose.local.yml` maps app and Redis ports to `127.0.0.1` for workstation access and must not be used on a VPS.
- Restrict audit PostgreSQL separately to the application audit publisher and authorized audit readers. Keep its credentials, connection budget, backups, and failure domain independent from application PostgreSQL.

## Initial connection and memory budgets

The repository defaults are conservative starting limits, not a substitute for load testing the chosen VPS size:

| Resource | Initial budget | Saturation behavior |
| --- | --- | --- |
| Application R2DBC pool per instance | 10 connections; maximum configurable value 20; 5-second acquire/create timeout | Requests fail within a bound; readiness and pool metrics identify the dependency problem |
| PostgreSQL application runtime login | 40 total connections | Additional connections are refused; reduce replicas or pool size before raising the limit |
| PostgreSQL migration login | 5 total connections | Migration job is serialized; excess sessions are refused |
| Redis synchronous command pool per app instance | 8 active; maximum configurable value 32; 3-second command, connect, and acquire timeouts | Commons Pool waits up to `max-wait`, then rejects acquisition |
| Redis reactive command pool per app instance | 8 active; maximum configurable value 32; 3-second command and connect timeouts | Lettuce async pool rejects immediately at its maximum; Commons Pool `max-wait` does not apply |
| Audit PostgreSQL pool per app instance | 4 connections; audit worker concurrency may not exceed this pool | Audit work waits/fails within a bound; the durable application outbox remains authoritative |
| Local Redis process | 384 MB max memory inside a 512 MB container; `allkeys-lru` | Rebuildable projections may be evicted and reconstructed from PostgreSQL |

For the initial 10-connection app pool and 40-connection runtime role limit, keep the production app at no more than four instances until a load test and the VPS connection budget approve a different scale. The database `max_connections` must account for the runtime role, migration sessions, health/administration access, and PostgreSQL reserved connections. The audit database needs its own calculation. Do not increase pool or role limits independently.

Spring Data Redis maintains distinct synchronous and asynchronous pools, each with a pool per Lettuce connection type. The application currently uses synchronous command connections for cache operations and asynchronous command connections for reactive Redis requests; both have the configured `APP_REDIS_POOL_MAX_ACTIVE` ceiling. This gives a standard command-connection maximum of 16 per app instance at the default value of 8, or 64 at the current maximum value of 32. Add the aggregate (`2 × APP_REDIS_POOL_MAX_ACTIVE × app replicas`) to Redis `maxclients` sizing, plus operator/monitoring reserve. If future code adds pub/sub or another connection type, include its additional pool in the budget. Reactive acquisition rejects immediately when its pool is exhausted; synchronous acquisition waits up to the configured `APP_REDIS_POOL_MAX_WAIT` (default 3 seconds).

The local Redis memory setting is for development Compose only. Set production Redis `maxmemory` below the measured VPS/container limit, leave memory for Redis allocator and operating-system overhead, and select the eviction policy only after confirming every stored key is disposable or reconstructible. The PostgreSQL notification outbox and device-token rows remain authoritative; Redis eviction or loss must not discard durable work.

## Role provisioning and connection checks

For an empty development volume, Compose supplies default role budgets (`40` runtime and `5` migration) to the initialization script. Values are validated before they are interpolated into role DDL.

For an existing database volume, use the protected operator session and the existing provisioning flow. Set `APP_POSTGRES_RUNTIME_CONNECTION_LIMIT` and `APP_POSTGRES_MIGRATION_CONNECTION_LIMIT` to approved numeric bounds along with the role credentials, then run:

```bash
./deploy/postgres/operations/provision-existing-volume.sh
```

The procedure applies and verifies the exact limits alongside role grants. It does not remove or recreate database objects. Stop if the report does not show the expected role limits. Never place passwords in the command line or shell history; use the protected session method in the PostgreSQL cutover runbook.

Monitor `pg_stat_activity`, `pg_settings.max_connections`, rejected connections, R2DBC pool allocated/acquired/pending gauges, Redis connected clients, command latency, memory, evictions, and rejected writes. Alert ownership and deployed dashboards are tracked by AP-0023/AP-0024. Do not expose raw diagnostics on public health endpoints.

## PostgreSQL backup and restore

Application and audit PostgreSQL require separate encrypted off-host backup repositories and independent restore tests. The production choice of WAL archive/backup tooling, repository endpoint, encryption key custody, retention, and schedule must be fixed in AP-0046/AP-0024 before production use. The architecture baseline is a full backup plus continuous WAL archiving for application PostgreSQL, with point-in-time recovery; audit PostgreSQL has its own full/WAL chain and retention.

For each staging drill, record:

1. Database role (application or audit), source backup/WAL range, backup timestamp, and encrypted off-host repository reference.
2. Restore start/end UTC times and the requested recovery point. Never restore over the live data directory.
3. Database startup, schema/migration state, row counts, and the measured RPO/RTO.
4. For application data, run the wallet-ledger reconciliation and domain fixture checks. For audit data, verify append-only permissions and idempotently replay a bounded set from the application outbox.
5. Keep the drill report free of credential values and private customer payloads. Record follow-ups with an owner and due date.

Do not claim a backup is verified because an archive uploaded successfully. A restore into an isolated database and data-integrity checks are required. Do not run a restore against a production volume without the approved incident/change procedure and a verified recovery point.

## Redis loss, recovery, and failover

Redis holds disposable or rebuildable data only. PostgreSQL owns notification intent, delivery state, device tokens, wallet data, and other business records. If Redis is unavailable, keep application behavior within the documented endpoint-specific policy; do not create an in-memory durability fallback.

In staging, prove recovery by removing only the disposable Redis instance/data, restarting the service, and observing the existing PostgreSQL-driven projection recovery. Verify notification attempts remain unchanged in PostgreSQL, device-token projections are rebuilt, and the application becomes ready only when required Redis dependencies are healthy. Record recovery time and any lost disposable rate-limit/cache entries.

The current four-VPS plan has one app/Redis VPS and does not yet provide cross-host Redis failover. AP-0046 must select and validate a replica/Sentinel or managed Redis design if automatic failover is required. Until then, document Redis host loss as a bounded outage followed by a rebuild, and do not represent local AOF or same-host replication as host-loss protection.

## Readiness gates and ownership

- AP-0026 remains In Progress until network/firewall access, TLS certificate trust, connection and memory budgets, encrypted off-host PostgreSQL backup/PITR, Redis failover/rebuild, and staging recovery are evidenced.
- AP-0023 owns deployed metrics, dashboards, retention, and private diagnostics. AP-0024 owns alert owners, escalation, RPO/RTO approval, and measured drills.
- AP-0046 owns the provider region, private network, firewall controls, VPS capacity, and infrastructure automation. Keep the separately controlled migration/deployment runner private; never expose PostgreSQL to make a workflow connect.
