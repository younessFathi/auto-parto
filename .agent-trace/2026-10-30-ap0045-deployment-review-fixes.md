# Prompt Trace: AP-0045 deployment review fixes

## Request Summary
Address six reviewed secret-delivery deployment issues and add a regression that exercises the real Ansible playbook against unchanged ciphertext and a failed retry.

## Work Performed
- Hardened generation identity checks and rescue cleanup so failed retries cannot remove a live secrets generation.
- Added per-host/per-secret-root serialization across deploy and migration cleanup.
- Added application bundle cardinality, destination uniqueness, and lexical numeric GID validation.
- Changed the production Compose bind to long form with `create_host_path: false`.
- Aligned migration cleanup host selection and role filtering with deployment.
- Added a disposable Testcontainers Ansible integration test for unchanged ciphertext, failed decrypt retry, preflight rejection, locking, and migration cleanup.
- Updated AP-0045 and operator documentation with the additional evidence and remaining staging limitations.

## Result
The new playbook-level regression passes, including the failed retry preserving readable active application and audit properties. Existing shell-helper coverage remains in place.

## Files Changed
- `.architecture-backlog/tickets/AP-0045-ansible-vault-vps-secrets.md`: recorded hardening evidence and remaining operational gates.
- `deploy/ansible/deploy-secret-bundles.yml`: guarded deployment and failure cleanup.
- `deploy/ansible/remove-migration-bundle.yml`: shared target selection, role guard, and lock.
- `deploy/ansible/README.md`: documented validation, locking, and mount behavior.
- `docker-compose.prod.yml`: disabled automatic creation of the active bind source.
- `docs/production-secrets-runbook.md`: documented first-activation behavior.
- `src/test/java/auto/parto/config/AnsibleVaultDeploymentIT.java`: real playbook regression coverage.
- `src/test/java/auto/parto/config/SecretsManagementConfigTest.java`: configuration and playbook assertions.

## Verification
- `./mvnw -B test`: passed (413 tests, 1 skipped).
- `./mvnw -B verify`: failed once on `OpenBaoImageDeploymentIT` with an HTTP connect timeout; all other reported tests, including the new Ansible IT, passed.
- `./mvnw -B -Dit.test=OpenBaoImageDeploymentIT verify`: passed on isolated rerun.
- `git diff --check`: passed.

## Unresolved Risks
- No staging or VPS deployment, rotation, recovery, or old-credential rejection drill has run.
- Full `verify` encountered an intermittent OpenBao image HTTP connect timeout; the isolated rerun passed.
- A crashed Ansible controller can leave a lock directory requiring operator confirmation and cleanup.
