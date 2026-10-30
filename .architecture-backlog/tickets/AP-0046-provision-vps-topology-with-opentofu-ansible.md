# AP-0046 Provision VPS Topology with OpenTofu and Ansible

- **Status:** Proposed
- **Type:** Technical Enhancement
- **Feature:** [Deployment and Secrets](../features/F07-deployment-and-secrets.md)
- **Linked Tickets:** [AP-0021 TLS Entry Point](AP-0021-tls-entrypoint.md), [AP-0022 OpenBao prototype disposition](AP-0022-openbao-secrets.md), [AP-0026 Data Infrastructure](AP-0026-data-infrastructure.md), [AP-0033 GitHub Actions Migration](AP-0033-github-actions-migration.md), [AP-0044 Cancelled OpenBao VPS Go-Live](AP-0044-openbao-vps-go-live.md), [AP-0045 Ansible Vault Delivery](AP-0045-ansible-vault-vps-secrets.md)
- **Source:** `docs/deployment-architecture.md` Deployment, PostgreSQL, Redis, Object Storage, Secrets, and Recovery

## Description

Create a repeatable infrastructure and host-configuration path for the planned four-VPS deployment, all with the selected VPS provider:

1. Application and Redis cache/projections.
2. Application PostgreSQL and local backup staging.
3. Audit PostgreSQL.
4. SeaweedFS S3-compatible object storage.

Use OpenTofu to create provider resources and Ansible to configure operating systems and deploy services. ADR-0001 selects operator-controlled Ansible Vault injection as the single initial production secret-delivery model; do not operate OpenBao as a second source for the same production credentials. Contabo was selected as the intended provider on 2026-10-30. This records the provider choice only; it does not establish a region, account owner, budget, or readiness approval.

### Readiness Blockers

AP-0046 remains Proposed. ADR-0001 resolves the secret model in favor of Ansible Vault, so the four-VPS topology remains the initial plan without a secret-service host. OpenBao is not part of this production topology. Before Ready, AP-0046 still requires the Contabo region/account/cost details, exact private-network and firewall setup, and disposable validation of the maintained OpenTofu provider and private routing. The accepted Vault limitations, independent password recovery controls, and plaintext-at-rest safeguards remain required.

Contabo documents Cloud VPS/VDS private networks and API-managed compute, firewall, and private-network resources ([API reference](https://api.contabo.com/), [private networking guide](https://help.contabo.com/en/support/solutions/articles/103000274523-how-can-i-create-a-private-network-for-my-contabo-server-), [firewall guide](https://docs.contabo.com/docs/network-services/firewall/)). Private networking is location-specific and requires a paid add-on per participating server; it has no built-in firewall, so host-level rules must restrict traffic on private interfaces. The provider firewall controls inbound public traffic and is managed through the API/control panel. Contabo publishes an OpenTofu/Terraform provider ([provider repository](https://github.com/contabo/terraform-provider-contabo)); its existence is not evidence that its maintained version safely manages every selected resource or lifecycle operation.

Before AP-0046 can move to Ready, record the selected Contabo region/location, account owner, cost estimate for all servers/add-ons/backups, private-network capability, firewall/API support, and successful validation of a maintained OpenTofu provider for the required resources. Resolve any provider API limitations and verify private routing and allowlist behavior in a disposable environment.

The state-changing migration/deployment runner is a separate control boundary from ordinary application-repository workflows, as required by AP-0033/F09. Its workflow and runner must be controlled outside application PR-editable YAML, and the runner must reach PostgreSQL over private/allowlisted networking. Never expose PostgreSQL publicly to make migrations work. Keep GitLab CI active until the protected migration/deployment path has successful end-to-end staging evidence.

### Ready Criteria

Keep Proposed until Contabo's region/location, account owner, cost estimate, private-network and firewall/API capabilities are recorded; and a maintained OpenTofu provider has been validated for all selected resources in an isolated disposable environment. ADR-0001 selects Ansible Vault and resolves the four-core-VPS topology; record the external state-changing runner/orchestrator boundary and prove its intended PostgreSQL path remains private before Ready.

The four VPSs provide host-level separation while remaining in one provider failure boundary. Encrypted PostgreSQL backups and WAL, audit backups, and SeaweedFS recovery data therefore need a verified copy outside that provider. Redis remains a disposable/rebuildable projection and is not a recovery source for PostgreSQL-owned business state.

## Acceptance Criteria

1. Before Ready, record the selected provider, region, private-network capabilities, firewall/API support, resource sizes, availability assumptions, monthly cost estimate, and account owner. Verify that a maintained OpenTofu provider can create, inspect, and safely update every required resource in a disposable environment.
2. Store OpenTofu configuration, provider lock file, and Ansible roles/playbooks in reviewed version control. Pin provider and collection versions. OpenTofu state must not be committed; treat state and plans as sensitive because providers may return secret-bearing attributes. Keep provider API credentials out of state and command-line arguments.
3. Define an explicit state recovery model before the first apply. For initial single-operator use, protect local state on encrypted operator storage and maintain an encrypted off-provider backup. If multiple operators need shared state, use an access-controlled remote backend with locking and recovery evidence. Do not put the sole state copy on infrastructure managed by that same state.
4. Provision the four approved VPS roles with private networking, firewall rules, DNS, storage volumes, and minimum required public ingress. Expose only the approved TLS entry point on the application VPS. PostgreSQL, audit PostgreSQL, Redis, SeaweedFS S3/admin, and host-management interfaces must be private or explicitly allowlisted. OpenBao is not in the selected production topology.
5. Apply Ansible roles idempotently to harden each host, install pinned Docker/Compose dependencies, create restricted service identities and directories, install reviewed configuration, and start the assigned services. Generate or maintain inventory from OpenTofu outputs using private addresses; validate SSH host keys and use least-privilege operator access.
6. Keep service placement and credentials scoped by role: application/Redis on the app VPS; application PostgreSQL and bounded local backup staging on the database VPS; audit PostgreSQL on its own VPS; SeaweedFS on the S3 VPS. Application, audit, migration, database administration, object-storage, and backup credentials remain distinct. Migration credentials are delivered only for migration operations and are not retained on the app host.
7. Configure TLS for service connections where supported and explicit allowlists for each network flow. Bound CPU, memory, disk, database connections, Redis memory/eviction, and object-storage capacity; configure monitoring and disk-pressure alerts. Redis loss must trigger the documented PostgreSQL projection rebuild path.
8. Send encrypted application PostgreSQL base backups and required WAL archives, and independent audit PostgreSQL backups, to a destination outside the database VPS. Maintain an additional encrypted copy outside the VPS provider. Back up SeaweedFS object data and required filer metadata/configuration to storage outside the S3 VPS and provider. Use separate, restricted backup identities and document retention and integrity verification.
9. Deliver runtime secrets using the AP-0045 Ansible Vault design: keep encrypted per-environment/per-role variables under reviewed access controls, keep the unlock password outside the repository and VPS, use `no_log: true` for secret-bearing tasks, and install only files needed by each workload with restrictive host permissions and read-only container mounts.
10. Provide safe lifecycle commands and documentation for `plan`, reviewed `apply`, drift inspection, host replacement, and recovery. Ordinary application deployment must not destroy or recreate database/object-storage volumes. Destructive infrastructure changes require a separate explicit reviewed plan and verified backups.
11. Demonstrate the topology in an isolated staging environment: repeated apply is idempotent; public probes cannot reach private services; an unapproved source address is blocked; host replacement can be rebuilt from code and protected state; application and audit PostgreSQL restore/PITR and wallet-ledger reconciliation pass; SeaweedFS data/metadata restore passes; Redis projections rebuild from PostgreSQL; and infrastructure rollback/recovery ownership is documented.
12. Run state-changing migrations/deployments only through the separately controlled runner/orchestrator; verify private PostgreSQL reachability and prevent application PR workflows from accessing that runner. Keep GitLab active until a protected end-to-end staging migration/deployment run succeeds.
13. Update deployment architecture, network diagram, backup/recovery runbooks, and F07/F09/AP-0026 tracking with provider-specific evidence. Do not mark production readiness complete from a successful `tofu plan` or configuration-only test.

## Test Scenarios

1. **Given** a valid provider account and reviewed configuration, **when** an operator runs OpenTofu plan/apply twice in an isolated staging project, **then** the four VPS roles for the Ansible Vault topology are created and the second plan reports no unintended changes.
2. **Given** a provider API token or required provider capability is missing, **when** provisioning starts, **then** it fails before changing resources and emits no credential value.
3. **Given** an application or audit service attempts an unapproved network path, **when** it connects to another VPS service, **then** the firewall rejects the connection and an authorized path succeeds.
4. **Given** the database VPS is lost, **when** operators rebuild it and restore from the off-provider base backup/WAL archive, **then** PITR completes within recorded RPO/RTO and wallet balances reconcile with the immutable ledger.
5. **Given** the audit VPS is lost, **when** operators restore its independent backup, **then** audit history and pending application outbox delivery recover according to the documented procedure.
6. **Given** the S3 VPS is lost, **when** operators restore object data and filer metadata from the independent copy, **then** application file references resolve and bounded reconciliation passes.
7. **Given** the Redis cache/projection is lost, **when** the app rebuild process runs, **then** projections recover from PostgreSQL and no business state depends on Redis persistence.
8. **Given** a state backend is unavailable or an apply is interrupted, **when** an operator follows the state recovery procedure, **then** no concurrent state writer proceeds and infrastructure changes are not guessed or blindly recreated.

## Definition of Done

ADR-0001 selects Ansible Vault and resolves the four-role topology; the VPS provider/private network/firewall/API and maintained provider integration are approved and validated; OpenTofu and Ansible configuration are pinned, reviewed, repeatable, and tested in staging; access boundaries and resource limits are verified; application/audit database and SeaweedFS recovery copies exist outside the provider and restore drills pass; state backup/recovery and operational owners are documented; the separately controlled state-changing runner has private database access and successful end-to-end staging evidence; GitLab remains active until equivalent protected workflow evidence exists and an approved retirement decision is recorded; F07/F09/AP-0026 reflect verified evidence; and focused checks plus `./mvnw -B test` and `./mvnw -B verify` pass where applicable. No ticket may be marked Done based only on local validation or a successful resource-creation plan.
