# AP-0047 Automated GitHub Deployments with Infisical

- **Status:** Proposed
- **Type:** Technical Enhancement
- **Feature:** [Go-Live Readiness](../features/F09-go-live-readiness.md)
- **Linked Tickets:** [AP-0033 GitHub Actions Migration](AP-0033-github-actions-migration.md), [AP-0045 Ansible Vault Delivery](AP-0045-ansible-vault-vps-secrets.md), [AP-0046 OpenTofu and Ansible VPS Topology](AP-0046-provision-vps-topology-with-opentofu-ansible.md), [AP-0021 TLS Entry Point](AP-0021-tls-entrypoint.md), [AP-0026 Data Infrastructure](AP-0026-data-infrastructure.md)
- **Decision:** Requires a product-owner-approved ADR that supersedes or amends [ADR-0001](../../docs/architecture-decisions/ADR-0001-production-secret-delivery.md) before implementation selects Infisical for production delivery.
- **Source:** `docs/deployment-architecture.md` Deployment, Secrets, Networking, and Recovery

## Description

Define and implement a controlled release path that, after a trusted application change is merged, runs the existing application checks, publishes an immutable image, deploys automatically to development, and promotes the same verified image to production through a protected approval gate. Use Infisical Cloud with GitHub Actions OIDC as the proposed secret source, subject to the architecture decision and readiness gates below.

The application repository remains the source for application code and ordinary CI. State-changing deployment and migration workflows must be owned by a separately controlled deployment repository or external orchestrator. Application pull-request code must not be able to edit, invoke with elevated identity, or obtain secrets from that deployment control. A runner that can reach private VPS or PostgreSQL networks must be isolated from ordinary application workflows by controls outside application-repository YAML.

ADR-0001 currently selects operator-controlled Ansible Vault injection. This ticket does not itself reverse that decision. Before implementation, the product owner must approve an ADR selecting exactly one initial production credential-delivery source: (a) Infisical Cloud/another explicitly approved protected runtime secret service, or (b) the existing Ansible Vault model with a separately approved automation unlock/retrieval design. Both systems must never deliver the same production credentials. If Ansible Vault remains selected, this ticket must be revised to use its approved delivery design or cancelled; GitHub Actions must not be assumed able to decrypt Vault by default.

Prefer Infisical Cloud for the initial evaluation to avoid operating another stateful service. Before Ready, evaluate account ownership, plan and recurring cost, data region/residency, contractual/security requirements, availability, audit retention, recovery, and vendor access. Self-hosting Infisical is a separate infrastructure choice: it brings its own service, PostgreSQL, Redis, backup, upgrade, and availability duties and cannot silently be placed within the existing four-VPS topology.

## Scope and Deployment Flow

1. Application-repository CI runs on pull requests and trusted pushes: compile, unit tests, Docker/Testcontainers verification, security checks already required by project policy, and packaging. It has no production secrets, private database route, or deployment-runner access.
2. After an approved merge, CI builds the application image once, associates it with the reviewed source commit, and publishes it by immutable digest. Promotion passes only the commit and digest to the separately controlled deployment system; it does not pass secret values.
3. The controlled deployment workflow automatically deploys that digest to the development environment, obtains only development-scoped values through a short-lived GitHub OIDC exchange, installs role-scoped secret bundles, starts the service, and verifies health/smoke checks.
4. Production promotion uses the exact digest already verified in development. A protected production environment requires a named reviewer approval before the deployment job receives production-scoped OIDC trust or secrets. Automatic production deployment without approval is out of scope unless separately approved.
5. A separate protected migration operation handles application and audit databases in the required order, validates an immutable reviewed image/migration bundle and backup attestation, uses least-privilege migration identities, and reaches PostgreSQL only over the private network. A failed migration blocks application rollout.
6. Deployment retains the existing secret-bundle protections: role-specific destinations, restrictive ownership and `0440` mode, atomic generation activation, read-only Compose mounts, no secret values in logs/artifacts/traces, and safe rollback that does not restore revoked credentials.

## Acceptance Criteria

1. Before implementation is Ready, an approved ADR explicitly selects one production secret-delivery model and documents why. If Infisical is selected, ADR-0001 and AP-0045/AP-0046/F07/F09 tracking are reconciled; no production credential has two delivery sources. If Ansible Vault is retained, its unlock/retrieval path is separately approved before any CI automation.
2. The deployment control plane is a separate tightly controlled repository or external orchestrator. Workflow ownership, write access, environment administration, and runner registration are restricted to named maintainers. External policy prevents application-repository pull requests or modified application workflows from dispatching privileged deployments or obtaining deployment-runner access.
3. Any runner with VPS or private PostgreSQL reachability is dedicated to the deployment control plane, isolated from untrusted jobs, and externally restricted to approved workflow/repository identities. Ephemeral execution may reduce persistence but is not accepted as a network or authorization boundary. PostgreSQL remains private; no public database exposure is introduced for CI.
4. App CI runs compile, unit tests, Docker/Testcontainers verification, and package checks before image publication. Published images are immutable and traceable to a reviewed commit; the deployment control receives only the commit and digest and deploys the same digest tested in development and production.
5. Before Ready, record the protected branch/tag policy that triggers development deployment and the release/ref policy for production promotion. Development deploys automatically after successful trusted-branch verification. Development and production use distinct Infisical environments/projects or equivalently isolated scopes and separate machine identities. Production promotion requires an environment approval and deploys only a digest already verified in development.
6. If Infisical is selected, GitHub OIDC machine identities use short-lived exchange and exact issuer, audience, repository, protected workflow/ref, and environment claims. No static Infisical token, Vault password, SSH private key, or database password is stored as an ordinary GitHub secret. A wrong repository, untrusted PR, wrong workflow/ref, wrong environment, or wrong audience is denied. Production identity cannot read development-only or unrelated project secrets, and vice versa.
7. Secret retrieval occurs only after the relevant deployment authorization. Secret-bearing task output is suppressed; values are not written to workflow logs, command arguments, artifacts, caches, traces, build layers, deployment metadata, or application-repository outputs. Document the bounded period values exist in the trusted job memory and on the target host while in use. Preserve AP-0045 atomic bundle activation, permission, cleanup, and rollback guarantees.
8. Environment scopes and identities separate application runtime, application migration, audit runtime/migration, database administration/bootstrap, object storage, backup, and deployment access. Runtime and migration credentials are not retained on the application host unless explicitly required and approved. Rotation replaces credentials, verifies new use and old-credential rejection, and records revocation/recovery ownership.
9. Production migration/deployment is serialized, auditable, and gated by verified backup evidence, migration outcome, health/smoke results, and rollback compatibility. Application and audit PostgreSQL remain separate failure/security boundaries. Migration failure or failed health checks prevent traffic promotion and leave a safe recovery path.
10. The runbook covers initial identity bootstrap, OIDC claim configuration, environment setup, development rollout, production approval/promotion, secret rotation/revocation, deployment failure and rollback, runner replacement, Infisical outage/recovery, and credential compromise. It identifies accountable operators and records tested recovery outcomes.
11. Keep GitLab CI active until the separately controlled GitHub deployment/migration path has completed successful end-to-end staging evidence and retirement is explicitly approved. Do not change AP-0033, AP-0045, AP-0046, F07, or F09 to Done based on workflow configuration alone.

## Readiness Blockers

- Product-owner-approved ADR resolving the conflict with ADR-0001; the selected production secret source must be singular.
- Decision on Infisical Cloud versus another approved service, including account owner, plan/cost, region/residency, contractual requirements, availability, audit retention, and recovery expectations.
- Separate deployment repository/orchestrator ownership and externally enforced runner/dispatch/network boundaries.
- AP-0046 provider, region, account, cost, private networking, firewall/API, and maintained OpenTofu-provider validation; four-VPS topology must be revisited if a self-hosted secret service is selected.
- Protected development/production environment ownership and approval policy; private runner access to PostgreSQL must be proven without public exposure.

## Test Scenarios

1. **Given** a pull request from an untrusted contributor, **when** it changes application workflow YAML or requests a privileged deployment, **then** it cannot obtain OIDC trust, secrets, the deployment runner, VPS access, or private database connectivity.
2. **Given** the approved deployment workflow and correct development claims, **when** CI and image publication succeed, **then** the deployment system retrieves only development-scoped values and deploys the published digest automatically.
3. **Given** a correct image digest verified in development, **when** production promotion is requested without reviewer approval, **then** no production credential is issued and no production host is changed; after approval, that same digest is deployed.
4. **Given** a valid identity with a wrong repository, workflow, ref, environment, issuer, or audience claim, **when** it requests secret access, **then** the request is denied and the denial is auditable without exposing values.
5. **Given** successful production credential rotation, **when** the new generation is activated, **then** the service becomes healthy, the prior credential is rejected, and rollback does not restore the revoked value.
6. **Given** migration failure, backup-attestation failure, image-health failure, or Infisical unavailability, **when** the protected release runs, **then** rollout stops safely, private PostgreSQL remains unexposed, no secret is emitted, and the documented recovery procedure works.
7. **Given** a deployment-runner replacement or recovery event, **when** the operator rebuilds it from the controlled configuration, **then** only the deployment control plane can schedule privileged jobs and access remains least-privilege.

## Definition of Done

An approved ADR selects the sole delivery model; protected staging proves application CI, immutable image publication, automatic development deployment, approved production promotion of the same digest, exact OIDC allow/deny behavior, external runner isolation, private migration access, secret redaction, rotation with old-value rejection, and failure/rollback recovery. Runbooks and AP-0033/AP-0045/AP-0046/F07/F09 tracking reflect verified evidence, GitLab remains active until equivalent end-to-end verification and approved retirement, and focused automated plus staging checks pass. This ticket is not Done merely because workflows, identities, or infrastructure are configured.
