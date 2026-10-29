# AP-0045 Use Ansible Vault for VPS Secret Delivery

- **Status:** Proposed
- **Type:** Technical Enhancement
- **Feature:** [Deployment and Secrets](../features/F07-deployment-and-secrets.md)
- **Linked Tickets:** [AP-0022 OpenBao Secret Management](AP-0022-openbao-secrets.md), [AP-0044 OpenBao VPS Go-Live](AP-0044-openbao-vps-go-live.md), [AP-0046 OpenTofu and Ansible VPS Topology](AP-0046-provision-vps-topology-with-opentofu-ansible.md)
- **Source:** `docs/deployment-architecture.md` Secrets and Configuration / OpenBao

## Description

Evaluate Ansible Vault as one mutually exclusive option for secret delivery in the initial four-VPS deployment. This ticket remains a decision gate until an Architecture Decision Record (ADR) selects exactly one model and records its rationale, threat boundary, operational limitations, and affected-ticket updates. The two permitted choices are:

1. **Ansible Vault with operator-controlled deploy-time injection:** an authorized operator unlocks encrypted variables on a trusted workstation and uses a reviewed Ansible playbook to install only required role-scoped files on the VPSs.
2. **OpenBao or another explicitly approved protected runtime secret service:** retain the protected service, workload identity, and runtime secret-delivery model described by AP-0022 and AP-0044.

Do not implement either option or change the current deployment architecture before the ADR is approved. Do not run both systems as sources for the same production credentials. Ansible Vault protects its encrypted source data at rest; it does not provide a runtime secret API, workload identity, dynamic credentials, lease renewal, or immediate centralized revocation.

If the ADR selects Ansible Vault, update linked tracking to supersede or defer AP-0044's initial OpenBao VPS delivery and re-baseline AP-0022's OpenBao-specific acceptance criteria against the approved delivery model. Keep AP-0022, AP-0044, and F07 short of Done until their revised or remaining acceptance criteria have evidence. If the ADR retains OpenBao/protected runtime service, cancel AP-0045 and record the decision; continue AP-0044 only after its topology prerequisites are met.

## Acceptance Criteria

1. Before implementation, create and approve an ADR selecting exactly one initial production secret-delivery model: operator-controlled Ansible Vault deploy-time injection, or OpenBao/an approved protected runtime secret service. Record rationale, threat/failure boundaries, operations and recovery ownership, limitations, and required backlog changes. No implementation begins before this ADR exists.
2. If Ansible Vault is selected, explicitly defer or supersede AP-0044's initial OpenBao VPS delivery path and re-baseline AP-0022 without marking AP-0022, AP-0044, or F07 Done. If OpenBao/protected runtime service is selected, cancel AP-0045 with the ADR reference and continue the approved OpenBao path. The two systems must never deliver the same production credentials.
3. For the Ansible Vault option, keep production values in encrypted Vault files with independent passwords for production and non-production environments. Keep the Vault password out of Git, all VPSs, command lines, logs, traces, and ordinary CI secrets. The default workflow is operator-controlled deploy-time injection from a trusted workstation. Any later automated deployment requires a separately approved secure unlock/retrieval design; GitHub Actions must not be assumed able to decrypt production Vault data.
4. For the Ansible Vault option, provide a reviewed, idempotent Ansible playbook that connects to allowlisted VPSs using verified SSH host keys, writes only enabled app/cache, application database/backup, audit database, S3, and migration credentials to their intended paths, and applies restrictive owner/group/mode settings. Secret-bearing tasks use `no_log: true` and do not print decrypted content.
5. Adapt production Compose delivery to mount only required secret files read-only into consuming workloads. Preserve current application imports under `/run/auto-parto/secrets/secrets.properties` and, when audit processing is enabled, `/run/auto-parto/audit/secrets.properties`, or update profile and fail-closed validation with equivalent tests. If Ansible Vault is selected, remove OpenBao-specific runtime endpoint/role requirements. Do not bake secrets into images, Compose values, or checked-in plaintext files.
6. Ensure production startup fails closed when required bundles or enabled-provider credentials are absent, malformed, or incomplete. Keep development/test secret sources separate; do not leave migration or database-administration credentials on the app VPS.
7. Document and exercise rotation: provision replacement credentials, deploy, restart and verify readiness, reject old credentials, and define bounded rollback without restoring revoked credentials.
8. Document encrypted backup and restore of Vault files, separate recovery of the Vault password, operator replacement/loss scenarios, and measured staging restore results. Store no decrypted backup in ordinary backups.
9. Preserve RS256/JWKS overlap and revocation behavior and include signing-key rotation/removal in staging. Document that secrets exist in plaintext on the VPS while in use, rotation/revocation require operator deployment, and Vault provides no dynamic leases or automatic workload-token renewal. Define host hardening, disk/backup protection, access ownership, and incident response for suspected host compromise.

## Status Transition

Keep Proposed until the ADR is approved. Move to Ready only if the ADR selects Ansible Vault and names the operator-controlled deployment path, password custody/recovery, role separation, and accepted host-at-rest limitations. If the ADR selects OpenBao/protected runtime service, cancel AP-0045 and record the ADR reference; do not mark it Done.

## Test Scenarios

1. **Given** an authorized operator and a valid encrypted production bundle, **when** the playbook runs against a disposable staging host, **then** only the intended files are installed with the required ownership and permissions and Compose mounts them read-only.
2. **Given** an incorrect Vault unlock password or malformed encrypted file, **when** deployment runs, **then** it fails before changing the active secret bundle and emits no secret values.
3. **Given** required application or enabled audit/provider secrets are missing, **when** the production app starts, **then** startup fails closed without exposing values.
4. **Given** a credential rotation, **when** the new bundle is deployed and the app is ready, **then** the replacement works, the old value is rejected, and the documented rollback does not re-enable a revoked credential.
5. **Given** loss of the deployment workstation or VPS, **when** an authorized operator restores the encrypted Vault file and rebuilds a replacement host, **then** the separate unlock-password recovery process works and the resulting deployment passes the readiness and access checks.
6. **Given** a signing-key rotation, **when** the new key is published and active, **then** tokens signed by allowed overlap keys verify and tokens from removed/revoked keys fail according to the documented policy.

## Definition of Done

If Ansible Vault is selected, the approved delivery is implemented; missing-secret, rotation, rejection, key-overlap, and recovery scenarios have automated or repeatable staging evidence; runbooks identify operators and recovery steps; AP-0022/AP-0044/F07 tracking reflects the selected model without claiming unverified production readiness; and focused tests, `./mvnw -B test`, and `./mvnw -B verify` pass in an environment with Docker access. If OpenBao is selected, AP-0045 is Cancelled with the ADR recorded and this ticket has no implementation DoD.
