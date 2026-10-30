# Production Secrets Runbook

## Selected model and current gate

[ADR-0001](architecture-decisions/ADR-0001-production-secret-delivery.md) selects operator-controlled Ansible Vault deploy-time injection for initial production. OpenBao is not selected to deliver these credentials. Do not use both systems for the same production secret.

This is the approved target procedure, not a claim that production delivery is ready for staging or production. AP-0045 has removed OpenBao-specific runtime requirements, made production depend on role-scoped mounted files, and added an initial Ansible playbook. The playbook runs in disposable Docker integration tests, but it has not been run against a provider staging host. AP-0045 remains In Progress; AP-0046 remains Proposed pending Contabo region, owner, cost, private-network/provider validation, and the separately controlled deployment path. Keep F07 In Progress and do not enable production traffic.

For local development only, `docker-compose.openbao-dev.yml` remains a loopback-only disposable sandbox with non-production values. Do not connect production workloads or credentials to it.

## Custody and access

- Keep separate encrypted Ansible Vault inputs and unlock passwords for production and non-production. Store encrypted inputs in the separately controlled infrastructure/deployment repository; never commit plaintext values.
- An authorized operator works from a trusted, patched workstation and unlocks Vault interactively. The password is held in an access-controlled password manager, separate from the encrypted input. Assign an operator custodian and an independent recovery custodian before staging deployment. The password must never be stored in Git, a VPS, command arguments, process environment, logs, traces, or ordinary CI secrets.
- Do not automate Vault unlock from GitHub Actions. A future automated unlock/retrieval mechanism requires a separately approved architecture and custody design.
- Keep SSH private keys and Contabo API credentials separate from Vault passwords and application credentials. Use least-privilege accounts, verified SSH host keys, and allowlisted source addresses. Keep application pull-request workflows unable to access the state-changing deployment path.
- Partition encrypted values by environment and workload role: application, application database administration/bootstrap, audit database, object storage, and migration. Runtime, audit, backup, and migration credentials are distinct. Migration credentials are installed only for a migration and removed after completion.

## Staging checklist: step by step

Staging means a temporary, non-production setup used to prove the deployment works. Use test accounts and test credentials only. Do not copy production credentials or real customer data into staging. The checklist is split into work that can be prepared now and work that starts after the VPS is delivered.

### Step 1: Prepare while waiting for the VPS

- [ ] Confirm who will operate staging and who is the separate recovery custodian. Both people must be able to follow the password-manager recovery process without recording or sharing the unlock password in chat.
- [ ] Keep the infrastructure repository, encrypted Vault files, inventory, and deployment access separate from the application repository. If that controlled repository is not ready yet, prepare its access and ownership plan; do not add real inventory addresses or Vault files here.
- [ ] Decide which staging services will be enabled. Keep optional integrations such as email, Firebase, and audit delivery disabled unless staging credentials and an isolated test destination are ready.
- [ ] Create a separate staging credential set at each enabled dependency. Use values that can be revoked without affecting production. Keep database, audit, object-storage, backup, migration, and application credentials distinct.
- [ ] Prepare encrypted properties file(s) for each target role listed in [the Ansible inventory contract](../deploy/ansible/README.md). Some roles need more than one file. Encrypt staging files with the staging Vault password; never reuse the production password.
- [ ] Confirm the staging application properties contain every required value for the enabled providers, using the production configuration validation as the checklist. Keep the Vault password in the password manager, separate from the encrypted files.
- [ ] Save a protected copy of the encrypted staging files. Separately verify that the operator and recovery custodian can retrieve the unlock password. Record pass/fail only, never the password or decrypted contents.
- [ ] Prepare reviewed, immutable application and migration artifacts. Write down the expected image/version and the exact inventory limit for each target role.

**What this step proves:** the test credentials, encrypted files, custodians, and release artifacts are ready. It does not prove a VPS is configured or reachable.

### Step 2: Confirm the VPS and network before sending secrets

- [ ] Record the provider-delivered host name/address, role, operating-system image, region, resource size, and account owner in the protected infrastructure records.
- [ ] Confirm the private network and firewall rules. Allow operator SSH only from the approved source address. Keep PostgreSQL, Redis, S3/admin, and host-management ports private or explicitly allowlisted; do not open PostgreSQL to the public Internet.
- [ ] Obtain each SSH host-key fingerprint through a trusted provider/control-panel channel, compare it with the host key seen by SSH, and add it to the verified known-hosts file. Do not accept a first-seen key without independent verification.
- [ ] Confirm host access works with the intended operator identity and key. Check only reachability and host identity at this point; do not send Vault files yet.
- [ ] Confirm the assigned service users/groups match the inventory GIDs. Create and protect `/run/lock` and the role-specific `/run` secret roots. The app runtime must be able to read its files but not write them.
- [ ] Confirm the staging database and object-storage test accounts exist and are isolated from production. Keep the migration runner separate from ordinary app deployment.

**Stop here if:** the SSH host key cannot be verified, the required private-network path is missing, a service port is publicly exposed without approval, or the service UID/GID does not match the inventory. Do not deliver secrets to resolve a network or identity mismatch.

### Step 3: Check Ansible and deploy one role at a time

- [ ] From the trusted operator workstation and separately controlled infrastructure repository, pin a reviewed Ansible Core version and run syntax-check on the reviewed playbook before contacting a host.
- [ ] Check connectivity to only the intended host with Ansible's ping module. This checks SSH/Ansible access; it does not read or display secrets.
- [ ] Deploy the application role first, using the staging inventory limit and interactive Vault prompt. Do not use verbose mode, `--diff`, a password file, environment variable, or command-line password value.
- [ ] Verify the playbook reports success. If it fails, keep the app stopped, read only the redacted failure summary, correct the cause, and retry. Never print a properties file to diagnose it.
- [ ] Repeat for application database, audit database, and object storage, each with its own inventory limit and only that role's bundle. Deploy migration credentials only immediately before a staging migration and run the cleanup playbook immediately after.
- [ ] For a healthy identical retry, confirm the active target and file metadata do not change. The playbook performs this validation; it must reject missing files or incorrect UID/GID/mode and release the lock.

**What this step proves:** the verified host accepts the encrypted staging bundle, decrypts only the files for its role, and installs them with the expected permissions.

For example, after the separately controlled infrastructure repository and staging inventory exist, syntax-check the playbook and deploy only the application host. Replace `staging-app` with that host's inventory alias; the unlock password is entered only at the interactive prompt:

```sh
ANSIBLE_CONFIG=deploy/ansible/ansible.cfg ansible-playbook \
  --syntax-check --inventory inventory/staging.yml \
  deploy/ansible/deploy-secret-bundles.yml

ANSIBLE_CONFIG=deploy/ansible/ansible.cfg ansible-playbook \
  --inventory inventory/staging.yml \
  deploy/ansible/deploy-secret-bundles.yml \
  --ask-vault-pass --limit staging-app
```

Repeat the deployment command with the inventory alias for each database, object-storage, or migration host. Do not use the example alias literally unless that is the actual inventory name.

### Step 4: Start the staging app and check it safely

- [ ] Start the app only after its application bundle is active. Compose must mount `/run/auto-parto/active` read-only; it must fail if that active path does not exist.
- [ ] Check container state with `docker compose --profile production ps`. Check readiness using the configured health check at `/actuator/health/readiness`; the health response should be status-only.
- [ ] Confirm the app runs as UID/GID `10001:10001`, can read but cannot write its mounted bundle, and can authenticate to each enabled staging dependency.
- [ ] Confirm PostgreSQL and Redis are reachable only over their approved private paths. Confirm the staging object-storage endpoint works without exposing its admin interface.
- [ ] If readiness fails, stop the app and inspect the redacted health status and task summary. Do not use `cat`, `env`, `docker inspect` on secret-bearing environment, `ansible -vvv`, or verbose logs that could reveal values.

**What this step proves:** the app starts with mounted staging files and can reach its enabled staging dependencies through the intended network paths.

### Step 5: Prove rotation and old-credential rejection

- [ ] Choose one staging credential whose dependency supports overlapping credentials. Issue a replacement while the old one still works.
- [ ] Put only the replacement into the encrypted staging bundle, review the encrypted change, deploy it, and restart the consuming workload.
- [ ] Confirm readiness and an authenticated dependency operation succeed with the replacement.
- [ ] Revoke the old credential at the dependency. Run a safe authentication check that prints only pass/fail and confirms the old value is rejected. Never put the credential in the command text or test output.
- [ ] Record the date, operator, dependency/role, and pass/fail. If rollout failed before revocation, rollback is allowed only during the approved overlap window. After revocation, issue another fresh credential; do not restore the revoked value.

### Step 6: Prove encrypted-input recovery and host recovery

- [ ] On a clean trusted workstation or a disposable replacement host, have the recovery custodian retrieve the encrypted staging files from the protected backup and the operator retrieve the unlock password through its separate recovery path.
- [ ] Unlock interactively and redeploy to a disposable replacement staging host. Confirm each role's files, owner/group/mode, app readiness, and private dependency paths.
- [ ] Confirm the encrypted backup contains no plaintext copy. Remove temporary decrypted files and revoke the old disposable host's access and credentials.
- [ ] Record the elapsed time, successful/failed checks, owner, and follow-up actions. Keep a redacted drill record only.

**Completion rule:** AP-0045 stays In Progress until the staging deployment, credential rotation and rejection, password recovery, and host recovery have recorded evidence. Passing local Docker tests alone is not enough.

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

The production profile now uses a required properties-file import and no longer requires an OpenBao URL, role, or auth method. The playbook is executed with pinned Ansible Core in a disposable Docker integration test, including role-scoped file delivery and retry failure cases. This does not verify deployment on a provider host. Password custody/recovery, Contabo provisioning, old-credential rejection, host rebuild, and end-to-end staging deployment have not been performed; those remain operational gates.
