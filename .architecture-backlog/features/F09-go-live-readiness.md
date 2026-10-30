# F09 Go-Live Readiness

Coordinate the final production readiness, database cutover, and VPS release gates before Auto-Parto serves live traffic.

**Status:** Deferred until a go-live decision is approved.

## Delivery Order for AP-0021, AP-0023, AP-0024, AP-0026, AP-0028, and AP-0033

1. **AP-0023** — application metrics, provider/background observations, structured redacted logs, safe probes, and a protected observability deployment template are implemented. AP-0023 remains In Progress until protected staging deployment, retention/deletion, quotas, and trace linkage are verified.
2. **AP-0033** — independent CI track, already In Progress. Complete and verify GitHub Actions compile, unit tests, Docker/Testcontainers verification, and packaging. Keep private database access and migration credentials out of application-repository workflows; implement the manual gate only in a separately controlled deployment repository/orchestrator, with a runner registered only there and protected workflow ownership. The gate must validate an immutable reviewed application commit and backup attestation, then run Flyway, fixtures, reconciliation, and cutover preflight in order. Keep `.gitlab-ci.yml` active until equivalent workflow runs are verified end to end; do not publish application packages as short-lived test-report artifacts.
3. **AP-0028 and AP-0026** — AP-0022's repository secret-delivery implementation is available, so these implementation tracks can proceed using isolated test credentials and providers. AP-0022 remains In Progress until its deployed service, workload identity, rotation, and recovery criteria are met; this implementation dependency does not permit go-live. AP-0028 also requires AP-0020 and AP-0017 (both Done); AP-0026 requires AP-0002 (Done) and AP-0016. These can proceed in parallel. AP-0028 uses Firebase Admin's async future API and the existing AWS `S3AsyncClient`; Resend remains synchronous and therefore runs on a dedicated bounded scheduler. Provider concurrency, timeouts, cancellation, bounded rejection, shutdown, correlation, and low-cardinality redacted telemetry are covered by the ticket's focused tests.
4. **AP-0021** — after AP-0028 and AP-0020. Configure the approved public TLS edge, strict-TLS origin proxy, trusted proxy addresses, health-gated routing, private data-service paths, edge rate limits, and SSE timeouts. Resolve the current Caddy recommendation in `docs/deployment-architecture.md` against the AP-0021 Nginx-origin requirement before deployment.
5. **AP-0024** — Blocked until AP-0021, AP-0022, AP-0023, and AP-0026 are demonstrably complete; AP-0013 and AP-0019 are Done. Then define each alert's named owner, severity, recovery objective, escalation path, and runbook. Independently back up and restore application and audit PostgreSQL, use PITR for business data and reconcile wallet balances with immutable ledger entries, restore audit history and replay its application outbox idempotently, rebuild Redis projections from PostgreSQL, and restore SeaweedFS volumes/filer metadata/IAM/key material before bounded file reconciliation. Exercise credential rotation, application rollback, duplicate notification delivery, and repeat each drill with recorded RPO/RTO, outcomes, and follow-up owners.

The deployment design keeps PostgreSQL, audit PostgreSQL, Redis, and object storage private and on explicitly defined failure boundaries. Recovery drills must record measured RPO/RTO, results, owners, and follow-up actions. CloudEvents remain out of scope unless an external consumer or cross-service contract is approved.

This feature is a release gate, not a replacement for the implementation tickets below. No production traffic should be enabled merely because the application builds or the PostgreSQL development bootstrap succeeds.

## Existing tickets contributing to go-live

- [AP-0002](../tickets/AP-0002-reactive-persistence.md): Complete the verified PostgreSQL application cutover.
- [AP-0020](../tickets/AP-0020-stateless-deployment.md): Deploy bounded non-root application replicas.
- [AP-0021](../tickets/AP-0021-tls-entrypoint.md): Configure TLS, trusted proxies, health checks, and SSE timeouts.
- [AP-0022](../tickets/AP-0022-openbao-secrets.md): Deliver and rotate protected production secrets.
- [AP-0044](../tickets/AP-0044-openbao-vps-go-live.md): Provision the production OpenBao service on its approved private failure boundary and complete workload, recovery, rotation, and alerting drills after go-live approval.
- [AP-0023](../tickets/AP-0023-telemetry-health.md): Provide production telemetry and health groups.
- [AP-0024](../tickets/AP-0024-alerts-recovery.md): Establish alerts, backups, restoration, and recovery drills.
- [AP-0026](../tickets/AP-0026-data-infrastructure.md): Harden PostgreSQL, Redis, and VPS database infrastructure.
- [AP-0027](../tickets/AP-0027-seaweedfs-hardening.md): Harden production object storage and recovery.
- [AP-0028](../tickets/AP-0028-provider-isolation.md): Isolate blocking provider integrations.

## Deferred go-live tasks

### Release and GitHub Actions deployment

- [ ] Build and publish an immutable application image and versioned migration bundle from the same commit.
- [ ] Add protected GitHub environments for staging and production.
- [ ] Add a serialized PostgreSQL preflight/recovery job with `resource_group` locking.
- [ ] Add a protected backup job before production migration.
- [ ] Add a dedicated Flyway migration job; a nonzero migration result must block application rollout.
- [ ] Make application deployment depend on successful backup, preflight, migration, and smoke checks.
- [ ] Select and document the deployment transport: GitHub-hosted or self-hosted runner, or restricted SSH to the VPS.
- [ ] Ensure deployment jobs use pinned artifacts and do not build from an uncommitted VPS checkout.
- [ ] Add separate application and audit database migration jobs with explicit ordering.

### VPS and network hardening

- [ ] Provision a hardened VPS with patched OS, least-privilege administration, firewall rules, SSH key access, and audit logging.
- [ ] Keep PostgreSQL, Redis, OpenBao, and internal storage private; expose only the approved TLS entry point.
- [ ] Configure persistent, monitored storage for PostgreSQL and verify disk-capacity alerts.
- [ ] Run application containers as non-root users with resource limits and restart policies.
- [ ] Document VPS replacement and rebuild procedures without relying on mutable container state.

### Secrets and database access

- [ ] Deliver production secrets through OpenBao, Docker Secrets, or an approved protected secret system.
- [ ] Keep bootstrap/DBA, migration, runtime, and audit credentials separate.
- [ ] Rotate credentials and verify that old credentials are rejected.
- [ ] Provision existing PostgreSQL volumes through the AP-0003 recovery procedure; never delete a volume to force initialization.
- [ ] Verify runtime grants, migration ownership, Flyway history access, and audit-database isolation before rollout.
- [ ] Confirm no secrets appear in GitHub Actions logs, Compose files, images, traces, or process arguments.
- [ ] Provision the separate TLS-protected OpenBao cluster, bind environment-scoped app/audit/migration workload identities, and record successful authorization, renewal/revocation, credential rotation, old-credential rejection, Raft snapshot restore, and unseal drills.

### Data cutover and rollback

- [ ] Complete PostgreSQL implementations and fixtures for every business repository.
- [ ] Decide explicitly which local data is recreated and which data, if any, is imported.
- [ ] Run idempotent import and identity, marketplace, wallet, and file reconciliation checks.
- [ ] Block cutover on any unresolved reconciliation result.
- [ ] Define the traffic-switch and PostgreSQL backup/rollback sequence only after PostgreSQL verification passes.
- [ ] Test expand-contract compatibility across overlapping application versions.
- [ ] Document forward-fix, application rollback compatibility, and database restore procedures.

### Backups, recovery, and operations

- [ ] Enable encrypted PostgreSQL backups, WAL/PITR where required, and off-VPS backup retention.
- [ ] Back up audit PostgreSQL independently from application PostgreSQL.
- [ ] Perform and record a restore drill, including wallet-ledger reconciliation.
- [ ] Test VPS loss, database failure, migration failure, secret-store failure, and audit-database outage scenarios.
- [ ] Configure dashboards and alerts for database health, replication/backup status, disk usage, migration failures, queue lag, and application readiness.
- [ ] Publish incident, rollback, credential-rotation, and disaster-recovery runbooks with named owners.

## Go-live approval gate

Go-live requires evidence that:

1. All linked implementation tickets and required security checks are complete.
2. Protected GitHub Actions jobs can deploy the exact tested artifact and block on migration or health failure.
3. PostgreSQL backups and restoration have been verified on the target VPS.
4. PostgreSQL fixtures, imports, and reconciliation checks pass with no unresolved errors.
5. Runtime, migration, audit, and administrative access are separated and tested.
6. TLS, monitoring, alerting, rate limits, resource limits, and recovery runbooks are operational.
7. Product and operations owners approve the traffic switch and rollback window.

Until these conditions are met, the system remains in development/staging mode. PostgreSQL is already the only application persistence authority; go-live work still protects deployment, backup, and recovery gates.
