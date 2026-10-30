# AP-0026 Harden PostgreSQL and Redis Infrastructure

- **Status:** Proposed
- **Type:** Technical Enhancement
- **Feature:** [Deployment and Secrets](../features/F07-deployment-and-secrets.md)
- **Linked Tickets:** [AP-0002](AP-0002-reactive-persistence.md), [AP-0016](AP-0016-redis-projections.md), [AP-0022](AP-0022-openbao-secrets.md), [AP-0046 VPS Infrastructure Automation](AP-0046-provision-vps-topology-with-opentofu-ansible.md)
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

## Definition of Done
The whole ticket is implemented, every acceptance criterion is satisfied, every scenario is automated or repeatably verified and passing, infrastructure runbooks are complete, and all applicable suites pass.
