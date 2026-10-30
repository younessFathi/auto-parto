# AP-0045 Use Ansible Vault for VPS Secret Delivery

- **Status:** Ready
- **Type:** Technical Enhancement
- **Feature:** [Deployment and Secrets](../features/F07-deployment-and-secrets.md)
- **Linked Tickets:** [AP-0022 OpenBao Secret Management](AP-0022-openbao-secrets.md), [AP-0044 OpenBao VPS Go-Live](AP-0044-openbao-vps-go-live.md), [AP-0046 OpenTofu and Ansible VPS Topology](AP-0046-provision-vps-topology-with-opentofu-ansible.md)
- **Decision:** [ADR-0001 Initial production secret delivery](../../docs/architecture-decisions/ADR-0001-production-secret-delivery.md)
- **Source:** `docs/deployment-architecture.md` Secrets and Configuration

## Description

ADR-0001 records the product owner's selection of operator-controlled Ansible Vault deploy-time injection for the initial four-VPS Contabo deployment. AP-0045 implements that model. The OpenBao production delivery path is superseded; no system may deliver the same production credentials in parallel. Ansible Vault protects encrypted source values at rest but does not provide runtime identity, dynamic credentials, leases, renewal, or immediate centralized revocation.

## Acceptance Criteria

1. Implement the accepted ADR-0001 decision; keep the Vault deployment model mutually exclusive with OpenBao for production credentials. AP-0022 and AP-0044 remain cancelled/superseded, and F07 remains In Progress until its active criteria have evidence.
2. Keep production values in encrypted Vault files with independent passwords for production and non-production environments. Keep the Vault password out of Git, all VPSs, command lines, logs, traces, and ordinary CI secrets. Unlock interactively from a trusted operator workstation using the separately controlled password-manager custody path. Any later automated deployment requires a separately approved secure unlock/retrieval design; GitHub Actions must not be assumed able to decrypt production Vault data.
4. For the Ansible Vault option, provide a reviewed, idempotent Ansible playbook that connects to allowlisted VPSs using verified SSH host keys, writes only enabled app/cache, application database/backup, audit database, S3, and migration credentials to their intended paths, and applies restrictive owner/group/mode settings. Secret-bearing tasks use `no_log: true` and do not print decrypted content.
5. Adapt production Compose delivery to mount only required secret files read-only into consuming workloads. Preserve current application imports under `/run/auto-parto/secrets/secrets.properties` and, when audit processing is enabled, `/run/auto-parto/audit/secrets.properties`, or update profile and fail-closed validation with equivalent tests. If Ansible Vault is selected, remove OpenBao-specific runtime endpoint/role requirements. Do not bake secrets into images, Compose values, or checked-in plaintext files.
6. Ensure production startup fails closed when required bundles or enabled-provider credentials are absent, malformed, or incomplete. Keep development/test secret sources separate; do not leave migration or database-administration credentials on the app VPS.
7. Document and exercise rotation: provision replacement credentials, deploy, restart and verify readiness, reject old credentials, and define bounded rollback without restoring revoked credentials.
8. Document encrypted backup and restore of Vault files, separate recovery of the Vault password, operator replacement/loss scenarios, and measured staging restore results. Store no decrypted backup in ordinary backups.
9. Preserve RS256/JWKS overlap and revocation behavior and include signing-key rotation/removal in staging. Document that secrets exist in plaintext on the VPS while in use, rotation/revocation require operator deployment, and Vault provides no dynamic leases or automatic workload-token renewal. Define host hardening, disk/backup protection, access ownership, and incident response for suspected host compromise.

## Status Transition

ADR-0001 is accepted and selects Ansible Vault with an operator-controlled deployment path, separate password-manager custody/recovery roles, role separation, and explicit plaintext-at-rest/no-lease limitations. The ticket is Ready for implementation. Do not mark it Done until deployment automation and required tests/drills pass.

## Test Scenarios

1. **Given** an authorized operator and a valid encrypted production bundle, **when** the playbook runs against a disposable staging host, **then** only the intended files are installed with the required ownership and permissions and Compose mounts them read-only.
2. **Given** an incorrect Vault unlock password or malformed encrypted file, **when** deployment runs, **then** it fails before changing the active secret bundle and emits no secret values.
3. **Given** required application or enabled audit/provider secrets are missing, **when** the production app starts, **then** startup fails closed without exposing values.
4. **Given** a credential rotation, **when** the new bundle is deployed and the app is ready, **then** the replacement works, the old value is rejected, and the documented rollback does not re-enable a revoked credential.
5. **Given** loss of the deployment workstation or VPS, **when** an authorized operator restores the encrypted Vault file and rebuilds a replacement host, **then** the separate unlock-password recovery process works and the resulting deployment passes the readiness and access checks.
6. **Given** a signing-key rotation, **when** the new key is published and active, **then** tokens signed by allowed overlap keys verify and tokens from removed/revoked keys fail according to the documented policy.

## Definition of Done

The approved delivery is implemented; missing-secret, rotation, rejection, key-overlap, and recovery scenarios have automated or repeatable staging evidence; runbooks identify operators and recovery steps; AP-0022/AP-0044/F07 tracking reflects the selected model without claiming unverified production readiness; and focused tests, `./mvnw -B test`, and `./mvnw -B verify` pass in an environment with Docker access.
