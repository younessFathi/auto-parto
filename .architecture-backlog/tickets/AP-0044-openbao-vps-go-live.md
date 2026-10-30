# AP-0044 Provision OpenBao for VPS Go-Live

- **Status:** Cancelled
- **Type:** Technical Enhancement
- **Feature:** [Deployment and Secrets](../features/F07-deployment-and-secrets.md)
- **Linked Tickets:** [AP-0022](AP-0022-openbao-secrets.md), [AP-0020](AP-0020-stateless-deployment.md), [AP-0021](AP-0021-tls-entrypoint.md), [AP-0024](AP-0024-alerts-recovery.md), [AP-0026](AP-0026-data-infrastructure.md), [AP-0033](AP-0033-github-actions-migration.md), [AP-0045 Secret Delivery Decision](AP-0045-ansible-vault-vps-secrets.md), [AP-0046 VPS Infrastructure Automation](AP-0046-provision-vps-topology-with-opentofu-ansible.md)
- **Source:** `docs/deployment-architecture.md` Secrets and Configuration / OpenBao

## Description

ADR-0001 selects operator-controlled Ansible Vault deploy-time injection for initial production. This OpenBao VPS delivery ticket is cancelled and superseded; no OpenBao service will deliver the initial production credentials. Retain any OpenBao image artifacts only as non-production prototypes until their disposition is handled under AP-0045. This cancellation is an architecture choice, not evidence of deployment or secret-management completion.

## Historical Acceptance Criteria (Superseded by ADR-0001)

1. Record the approved VPS/host topology, owner, threat boundary, availability target, and dependency/failure boundaries. OpenBao is on a dedicated secret-service host or an explicitly approved protected service, separate from application and audit database failure domains.
2. Deploy a pinned, verified OpenBao image or package in normal server mode with TLS certificate validation, private network/firewall rules, persistent encrypted Raft storage, non-root execution where supported, resource limits, and monitored disk capacity. No public listener is exposed.
3. Complete initialization and unseal ceremony with recovery material split and stored outside the VPS; no root token, unseal key, or recovery material appears in container arguments/environment, Compose files, logs, images, CI output, application containers, or traces. Revoke the initial root token after configuring scoped operators and policies.
4. Configure separate environment/application, audit, and migration identities and least-privilege policies. Deliver workload bootstrap material through the approved protected host mechanism; keep renewable Agent tokens on the host and render only scoped credential bundles with restrictive permissions. Verify cross-environment and cross-role access is denied.
5. Integrate the app, audit, and migration Agents with their respective services. Exercise startup gating, delayed Agent login/render, lease renewal failure, revocation, and application recovery without exposing secrets.
6. Configure encrypted, integrity-checked Raft snapshots and off-VPS backup retention. Complete a restore and unseal drill on an isolated replacement instance, recording measured RPO/RTO, integrity checks, operator roles, and recovery outcome.
7. Exercise runtime database/provider credential rotation, application rollout/rollback, and old-credential rejection. Confirm JWT key overlap and revocation behavior, and ensure application readiness gates the retirement of old credentials.
8. Establish named service ownership, paging/escalation, TLS/expiry, seal state, backup freshness, storage, availability, and lease-renewal alerts with tested runbooks.
9. Verify the exact production deployment artifact and configuration through the protected/manual release workflow; record approval, evidence links, and rollback procedure. Keep non-production OpenBao dev mode isolated and disposable.

## Test Scenarios

1. **Given** an approved deployment topology, **when** the production service is deployed, **then** only authorized private-network clients can reach its TLS endpoint and certificate verification succeeds.
2. **Given** an application workload identity, **when** it requests application credentials, **then** it receives only its environment-scoped paths and cannot read audit, migration, or another environment's secrets.
3. **Given** Agent startup is delayed or renewal fails, **when** the rendered bundle is absent, expired, or incomplete, **then** application startup/rollout fails closed without leaking values and recovers after valid rendering.
4. **Given** a production credential rotation, **when** the replacement is applied and the application reports readiness, **then** the previous credential is rejected and rollback behavior is documented and exercised.
5. **Given** loss of the OpenBao host, **when** operators restore a protected snapshot to an isolated replacement and unseal it with the approved recovery process, **then** policies and required secret paths recover within the recorded RPO/RTO.
6. **Given** the development Compose profile, **when** its disposable OpenBao dev container restarts, **then** its data loss is expected, it is not connected to production networks or credentials, and no production workflow depends on it.

## Definition of Done

This ticket was cancelled before any production OpenBao service was provisioned. Its historical criteria are not active and no completion evidence is claimed. AP-0045 implements the selected Ansible Vault path; AP-0022 is cancelled/superseded, and F07 remains In Progress.
