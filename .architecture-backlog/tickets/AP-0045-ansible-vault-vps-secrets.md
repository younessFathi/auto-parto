# AP-0045 Use Ansible Vault for Single-VPS Secret Delivery

- **Status:** Proposed
- **Type:** Technical Enhancement
- **Feature:** [Deployment and Secrets](../features/F07-deployment-and-secrets.md)
- **Linked Tickets:** [AP-0022 OpenBao Secret Management](AP-0022-openbao-secrets.md), [AP-0044 OpenBao VPS Go-Live](AP-0044-openbao-vps-go-live.md)
- **Source:** `docs/deployment-architecture.md` Secrets and Configuration / OpenBao

## Description

Evaluate and, if explicitly approved, implement a simpler secret-delivery path for the initial single-VPS deployment using Ansible Vault. The operator keeps an encrypted production variables file and deploys only the required credential files to the VPS through a reviewed Ansible playbook. The application receives those files through read-only Compose mounts and continues to fail startup when required secrets are absent or invalid.

This is an alternative to the OpenBao deployment path, not a second parallel secret architecture. Ansible Vault encrypts data at rest for deployment; it does not provide a runtime secret API, workload identity, dynamic credentials, lease renewal, or immediate centralized revocation. AP-0022 and AP-0044 remain OpenBao-based until the architecture owner explicitly selects this alternative and updates their scope and acceptance criteria. Do not mark AP-0022 or F07 Done solely because this ticket is implemented.

## Acceptance Criteria

1. Before implementation, record an explicit architecture decision selecting Ansible Vault for the initial VPS or retaining OpenBao. If OpenBao is retained, cancel this alternative with the decision recorded; do not operate both delivery systems for the same production credentials.
2. Keep production values in an encrypted Ansible Vault file with separate vault IDs for production and other environments where needed. Keep the vault unlock password outside the repository, VPS, CI variables, command arguments, logs, and traces; document secure operator access and recovery of that password.
3. Provide a reviewed, idempotent Ansible playbook that connects to an allowlisted VPS using verified SSH host keys, writes only the enabled application/audit/migration credential bundles to their intended host paths, and applies restrictive owner/group/mode settings. Tasks that handle secret material use `no_log: true` and do not print decrypted content.
4. Adapt the production Compose delivery to mount only required secret files read-only into the workload that consumes them. Preserve the current application imports under `/run/auto-parto/secrets/secrets.properties` and, when audit processing is enabled, `/run/auto-parto/audit/secrets.properties`, or update the profile and fail-closed validation with equivalent focused tests. Remove OpenBao-specific runtime endpoint/role requirements from the production profile if this alternative is selected. Do not bake secrets into images, Compose values, or checked-in plaintext files.
5. Ensure production startup fails closed when required bundles or enabled-provider credentials are absent, malformed, or incomplete. Keep development and test secret sources separate from the production Vault file.
6. Document and exercise credential rotation: provision a replacement credential, deploy the replacement, restart and verify readiness, reject the old credential, and describe a bounded rollback path that does not restore a revoked credential.
7. Document encrypted backup and restore of the Vault file, separate recovery of the unlock password, operator replacement/loss scenarios, and measured staging restore results. Store no decrypted backup in ordinary backups.
8. Preserve RS256/JWKS overlap and revocation behavior. Include signing-key rotation and old-key removal in the staging deployment drill.
9. Clearly document the accepted operational limits: secrets exist in plaintext on the VPS while in use; rotation and revocation require an operator deployment; there are no dynamic leases or automatic workload-token renewal. Define host hardening, encrypted disk/backup controls, access ownership, and incident response for suspected host compromise.

## Test Scenarios

1. **Given** an authorized operator and a valid encrypted production bundle, **when** the playbook runs against a disposable staging host, **then** only the intended files are installed with the required ownership and permissions and Compose mounts them read-only.
2. **Given** an incorrect Vault unlock password or malformed encrypted file, **when** deployment runs, **then** it fails before changing the active secret bundle and emits no secret values.
3. **Given** required application or enabled audit/provider secrets are missing, **when** the production app starts, **then** startup fails closed without exposing values.
4. **Given** a credential rotation, **when** the new bundle is deployed and the app is ready, **then** the replacement works, the old value is rejected, and the documented rollback does not re-enable a revoked credential.
5. **Given** loss of the deployment workstation or VPS, **when** an authorized operator restores the encrypted Vault file and rebuilds a replacement host, **then** the separate unlock-password recovery process works and the resulting deployment passes the readiness and access checks.
6. **Given** a signing-key rotation, **when** the new key is published and active, **then** tokens signed by allowed overlap keys verify and tokens from removed/revoked keys fail according to the documented policy.

## Definition of Done

The architecture decision is recorded; the selected Ansible Vault delivery and least-privilege file mounts are implemented; missing-secret, rotation, rejection, key-overlap, and recovery scenarios have automated or repeatable staging evidence; runbooks identify operators and recovery steps; AP-0022/AP-0044/F07 tracking reflects the selected model without claiming unverified production readiness; and focused tests, `./mvnw -B test`, and `./mvnw -B verify` pass in an environment with Docker access.
