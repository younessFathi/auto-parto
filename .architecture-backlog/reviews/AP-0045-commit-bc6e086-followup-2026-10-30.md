# AP-0045 Commit Review Follow-up: `bc6e086`

- **Ticket:** [AP-0045 Use Ansible Vault for VPS Secret Delivery](../tickets/AP-0045-ansible-vault-vps-secrets.md)
- **Related Review:** [Original review of `bc6e086`](AP-0045-commit-bc6e086-review-2026-10-30.md)
- **Review Status:** Required code corrections addressed; operational validation remains open
- **Implementation Status:** Follow-up correction committed separately
- **Reviewed At:** 2026-10-30

## Outcome

The three code findings are addressed. Application and optional audit bundles now require GID `10001`, matching the production container identity. The playbook stages and validates a full inactive generation before switching the active symlink, and role-specific destinations are allowlisted. Docker integration tests demonstrate read-only access for UID/GID `10001`, preservation of the active generation when a required later bundle is absent, and rejection of a path traversal destination. AP-0045 remains In Progress because Ansible syntax execution and the actual staging, rotation, and recovery drills are not available in this environment.

## Findings

### High

1. **Addressed.** [The playbook requires GID `10001` for both application bundle roles and verifies the staged file's numeric GID](../../deploy/ansible/deploy-secret-bundles.yml#L37). [Compose mounts the active generation read-only and runs as `10001:10001`](../../docker-compose.prod.yml#L20). [The Docker integration test verifies that this process can read the bundle but cannot write it](../../src/test/java/auto/parto/config/SecretsBundleDeploymentIT.java#L23).

### Medium

1. **Addressed in code and simulated integration coverage.** [All decrypted files are written to an inactive generation before file validation and activation](../../deploy/ansible/deploy-secret-bundles.yml#L96). Failed staging removes only that generation and returns a generic failure [without changing the active link](../../deploy/ansible/deploy-secret-bundles.yml#L159). The [activation helper validates all expected files before an atomic link replacement](../../deploy/ansible/activate-secret-generation.sh#L17); the Docker integration test stages a generation missing its later audit bundle and confirms the active generation remains unchanged [here](../../src/test/java/auto/parto/config/SecretsBundleDeploymentIT.java#L62). The test exercises the activation boundary, but does not run Ansible decryption against a malformed Vault ciphertext.

### Low

1. **Addressed.** [The playbook maps each bundle role to one fixed relative destination](../../deploy/ansible/deploy-secret-bundles.yml#L15) and validates it against the selected host role [before staging](../../deploy/ansible/deploy-secret-bundles.yml#L37). The activation helper separately rejects non-allowlisted paths, with a traversal attempt covered by the Docker integration test [here](../../src/test/java/auto/parto/config/SecretsBundleDeploymentIT.java#L90).

## Acceptance Criteria

| Criterion | Result | Evidence |
|---|---|---|
| Ansible Vault remains mutually exclusive with production OpenBao | Pass | Prior review evidence remains valid; no OpenBao runtime wiring was restored. |
| Application user can read the bundle without write access | Pass | `SecretsBundleDeploymentIT.applicationIdentityCanReadButCannotWriteMountedBundle`; production Compose user and read-only mount. |
| All bundle files stage before active generation changes | Pass for activation boundary; Ansible execution pending | Playbook block stages and validates every item before helper invocation; Docker test proves an incomplete second path cannot activate. |
| Destinations are constrained by role | Pass | Exact `secret_bundle_paths` and helper allowlist; negative traversal test. |
| Ansible syntax, malformed ciphertext behavior, and controlled staging execution | Not verified | `ansible-playbook` is unavailable; no controlled inventory/host is configured. The in-repo integration test covers the failure boundary with an incomplete staged generation. |
| Operational rotation, rollback, password recovery, and host recovery | Not verified | Remains AP-0045 staging evidence; ticket remains In Progress. |

## Verification

- `sh -n deploy/ansible/activate-secret-generation.sh` — passed.
- `git diff --check` — passed.
- `./mvnw -B -Dtest=ProductionConfigValidationTest,SecretsManagementConfigTest,ArchitectureCatalogTest test` — passed: 26 tests.
- `./mvnw -B -Dit.test=SecretsBundleDeploymentIT failsafe:integration-test failsafe:verify` — passed: 2 Docker integration tests; rerun after adding destination traversal coverage.
- `./mvnw -B test` — passed: 413 tests, 0 failures/errors, 1 skipped.
- `./mvnw -B verify` — passed: 147 integration tests, 0 failures/errors, 1 skipped. This ran before the final added traversal assertion; the targeted Docker integration test passed afterward.
- `ansible-playbook --syntax-check` — not run; Ansible is not installed.

## Required Before Approval

1. Run Ansible syntax/lint and the documented playbook against a controlled disposable staging host, including a malformed second encrypted source, and retain sanitized evidence.
2. Complete the password custody/recovery, credential rotation and old-credential rejection, migration cleanup, JWT overlap/revocation, and host recovery drills required by AP-0045.

## Residual Risks

- The Docker tests execute the actual activation helper and mount/permission behavior, but they do not execute the Ansible playbook or its Vault decryption task.
- Ansible remains uninstalled and there is no staging VPS/inventory. No production secrets or cloud resources were used.
- AP-0045 and F07 remain In Progress; this follow-up does not constitute production readiness evidence.
