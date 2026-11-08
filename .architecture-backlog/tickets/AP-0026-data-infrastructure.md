# AP-0026 Harden PostgreSQL and Redis Infrastructure

- **Status:** In Progress
- **Type:** Technical Enhancement
- **Feature:** [Deployment and Secrets](../features/F07-deployment-and-secrets.md)
- **Linked Tickets:** [AP-0002](AP-0002-reactive-persistence.md), [AP-0016](AP-0016-redis-projections.md), [AP-0045 Ansible Vault credential delivery](AP-0045-ansible-vault-vps-secrets.md), [AP-0046 VPS Infrastructure Automation](AP-0046-provision-vps-topology-with-opentofu-ansible.md)
- **Source:** `deployment-architecture.md` PostgreSQL and Redis Persistence

## Description
Secure, bound, and monitor production PostgreSQL and Redis without exposing either service publicly.

## Acceptance Criteria
1. PostgreSQL and Redis accept only private-network or allowlisted application traffic using authenticated encrypted connections.
2. R2DBC pooling, database connection limits, Redis memory limits, and eviction behavior are sized and monitored.
3. PostgreSQL has off-host backups and Redis has replication/failover appropriate to reconstructible data.
4. Neither service is exposed through the public load balancer or public Internet.
5. Initial PostgreSQL production deployment uses a dedicated VPS isolated from application containers, with a documented path to managed or highly available PostgreSQL.

## Test Scenarios
1. **Given** a public network client, **when** it connects, **then** PostgreSQL and Redis are unreachable.
2. **Given** connection or memory saturation, **when** limits are reached, **then** bounded failure and alerts occur.
3. **Given** Redis failover, **when** recovery completes, **then** reconstructible projections can resume or rebuild.

## AP-0001 Review Follow-up

Provision the separate audit PostgreSQL resource with independent credentials, backups, monitoring, retention, privacy/redaction workflow, legal-hold handling, and approved deletion controls. Keep audit storage and application storage on separate resource and failure boundaries.

## Implementation Tracking

- Local configuration hardening now binds Redis to no host interface by default, provides optional workstation access only through the loopback-only local Compose override, and caps the local Redis process below its container memory limit with `allkeys-lru`. PostgreSQL and audit PostgreSQL remain un-published in the Compose files.
- Production startup now requires verified TLS for the application PostgreSQL URL (`sslMode=VERIFY_FULL`), requires verified TLS for an enabled audit PostgreSQL URL, and requires Redis TLS to use a named PEM trust bundle. The Redis Lettuce factory explicitly loads that CA and enforces full certificate/hostname verification.
- Production PostgreSQL and Redis client pools now have finite size and timeout settings with fail-closed validation. Commons Pool 2 is pinned as a runtime dependency. Spring Data Redis maintains separate synchronous and asynchronous pools (and pools by connection type), each bounded by `APP_REDIS_POOL_MAX_ACTIVE`; the standard command paths therefore allow 16 active connections per app instance by default, up to 64 at the current maximum. Synchronous acquisition honors the configured wait; reactive acquisition rejects immediately at saturation. The runbook now accounts for the aggregate across replicas. A configuration test checks the effective per-pool ceiling and TLS verification; a Testcontainers regression uses the configured application factory to exercise mixed access, both saturation contracts, and pool reuse. Audit PostgreSQL uses its own bounded four-connection pool, bounded to the audit worker concurrency, with low-cardinality pool-state gauges. Application runtime and migration PostgreSQL roles receive independently validated connection limits in first-volume initialization and the existing-volume provisioning path.
- Added the [PostgreSQL and Redis Operations Runbook](../../docs/postgresql-redis-operations-runbook.md) with initial budget calculations, private access/TLS requirements, restoration evidence, and Redis loss behavior. Local PostgreSQL/Redis Compose limits and existing application tests are not production sizing or restore evidence.
- Still open: deploy/private-network/firewall verification; certificate provisioning and trust verification; measured DB, audit, and Redis sizing; encrypted off-host PostgreSQL backup/PITR and restore; production Redis replica/failover choice; Redis loss/rebuild staging drill; dashboard/alert verification. The four-VPS plan currently has no cross-host Redis failover. These require AP-0046 and a staging environment; do not mark this ticket Done.

## Definition of Done
The whole ticket is implemented, every acceptance criterion is satisfied, every scenario is automated or repeatably verified and passing, infrastructure runbooks are complete, and all applicable suites pass.
