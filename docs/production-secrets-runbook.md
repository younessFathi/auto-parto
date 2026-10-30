# Production Secrets Runbook

## Selected model and current gate

[ADR-0001](architecture-decisions/ADR-0001-production-secret-delivery.md) selects operator-controlled Ansible Vault deploy-time injection for initial production. OpenBao is not selected to deliver these credentials. Do not use both systems for the same production secret.

This is the approved target procedure, not a claim that production delivery is ready for staging or production. AP-0045 has removed OpenBao-specific runtime requirements, made production depend on role-scoped mounted files, and added an initial Ansible playbook. That playbook has not yet been run with Ansible or on a staging host. AP-0045 remains In Progress; AP-0046 remains Proposed pending Contabo region, owner, cost, private-network/provider validation, and the separately controlled deployment path. Keep F07 In Progress and do not enable production traffic.

For local development only, `docker-compose.openbao-dev.yml` remains a loopback-only disposable sandbox with non-production values. Do not connect production workloads or credentials to it.

## Custody and access

- Keep separate encrypted Ansible Vault inputs and unlock passwords for production and non-production. Store encrypted inputs in the separately controlled infrastructure/deployment repository; never commit plaintext values.
- An authorized operator works from a trusted, patched workstation and unlocks Vault interactively. The password is held in an access-controlled password manager, separate from the encrypted input. Assign an operator custodian and an independent recovery custodian before staging deployment. The password must never be stored in Git, a VPS, command arguments, process environment, logs, traces, or ordinary CI secrets.
- Do not automate Vault unlock from GitHub Actions. A future automated unlock/retrieval mechanism requires a separately approved architecture and custody design.
- Keep SSH private keys and Contabo API credentials separate from Vault passwords and application credentials. Use least-privilege accounts, verified SSH host keys, and allowlisted source addresses. Keep application pull-request workflows unable to access the state-changing deployment path.
- Partition encrypted values by environment and workload role: application, application database administration/bootstrap, audit database, object storage, and migration. Runtime, audit, backup, and migration credentials are distinct. Migration credentials are installed only for a migration and removed after completion.

## Deploy and verify

1. Obtain the approved immutable application/migration artifacts and verify the change is authorized. Confirm the target inventory and SSH host keys through the trusted operations path.
2. Run the reviewed, pinned Ansible playbook from the separately controlled infrastructure/deployment repository. Unlock Vault interactively; do not place the password value in a command or environment variable. Secret-bearing tasks set `no_log: true`, disable diff output, and fail without printing decrypted data.
3. Preflight all encrypted sources, stage role-scoped files under an inactive generation, and validate root ownership, numeric service GID, and mode before activation. Keep the production app bundle on the app VPS only; keep audit and database administration bundles on their respective hosts. Never place migration or database administrator credentials on the app VPS.
4. Activate the completed generation with the atomic active-link switch. Compose uses a long-form read-only bind with `create_host_path: false` for `/run/auto-parto/active`; if it has not been activated yet, Compose fails instead of creating a directory that blocks the link. It mounts at `/run/auto-parto`, preserving the application import at `/run/auto-parto/secrets/secrets.properties` and optional audit import at `/run/auto-parto/audit/secrets.properties`. Recreate/restart the container after activation so it uses the new mount target. Do not put values in Compose YAML, image layers, shell arguments, or general-purpose environment dumps.
5. Start/restart the workload only after the complete bundle is installed. Verify application readiness and authenticated dependency probes. Missing, malformed, or incomplete required files must block startup without exposing values.
6. For migration, deliver a distinct short-lived migration bundle only to the separately controlled migration runner over private PostgreSQL networking. Remove the bundle and revoke/rotate its credentials after the operation. PostgreSQL must not be made public to accommodate the runner.

## Rotation and rollback

1. Issue replacement credentials at the dependency while the current credential remains valid.
2. Update the encrypted role-scoped input, review the encrypted change, and deploy it through the operator-controlled playbook.
3. Verify readiness and dependency authentication with the replacement. Confirm the old credential is rejected, then remove it from the dependency.
4. If rollout fails before old-credential revocation, restore the prior encrypted version only within the approved overlap window. After revocation, never restore the old value; issue a fresh credential and deploy it.
5. Rotate JWT signing keys with the existing RS256/JWKS overlap policy: publish the new public key before switching `kid`, verify both overlap keys, switch the signer, then remove/revoke the old key after the maximum token lifetime plus clock skew. Verify tokens signed by a removed key fail. Keep private key material only in the encrypted application-role input.

## Backup and recovery

- Back up encrypted Vault inputs to a protected repository and an encrypted off-provider backup. Do not retain decrypted bundles in backups, artifacts, workstation temporary files, or VPS backups.
- Back up the unlock password through the password manager's independent recovery mechanism, with a separate recovery custodian. Never store the password alongside the encrypted inputs. Validate access to both recovery paths without recording password values.
- On workstation loss, revoke its SSH/API access, recover the encrypted inputs and password via their separate custody paths on a replacement trusted workstation, verify host keys, then redeploy. On VPS loss, rebuild from reviewed infrastructure code and redeploy only role-scoped bundles.
- Before production, record and exercise a staging restore drill: recover encrypted inputs, unlock them interactively, rebuild a disposable host, install the expected scoped files, verify permissions and readiness, and confirm no decrypted artifact remains. Record owner, elapsed time, results, and follow-up actions.

## Accepted limitations and incident response

Ansible Vault encrypts stored inputs but does not provide runtime identity, dynamic credentials, leases, renewal, or immediate centralized revocation. Secret values exist in plaintext on destination hosts and within workloads while in use. Rotation and revocation require an operator deployment. A host root compromise may expose that host's credentials; isolate the host, revoke its SSH and dependency credentials, issue replacements, rebuild it, review access records, and document the incident before resuming service.

## Evidence status

The production profile now uses a required properties-file import and no longer requires an OpenBao URL, role, or auth method. Focused tests cover the configuration contract and playbook safety markers; Ansible itself is unavailable in this environment, so playbook syntax/execution has not been verified. Password custody/recovery, Contabo provisioning, old-credential rejection, host rebuild, and end-to-end deployment have not been performed. Repository documentation and unit tests alone are not deployment evidence.
