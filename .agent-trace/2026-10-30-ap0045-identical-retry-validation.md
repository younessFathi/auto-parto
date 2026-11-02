# Prompt Trace: AP-0045 identical retry validation

## Request Summary
Validate active secret bundle files before an identical-generation retry can succeed, and add real-playbook regression coverage for missing and incorrectly permissioned files.

## Work Performed
- Added metadata-only active-generation file checks under the existing per-root lock, with checksum collection disabled and symlink following disabled.
- Kept validation failure outside the staging rescue so the live generation is never routed through deletion; the outer `always` releases the lock.
- Added safe role and relative-path diagnostics.
- Extended the Ansible Testcontainers regression to verify unchanged target/file identity/mtime on healthy retry, missing application bundle rejection, mode `0644` rejection, audit file preservation, unchanged active target, lock release, and preservation of prior regression scenarios.
- Updated AP-0045 evidence.

## Result
The focused real-playbook integration test and full Maven verification suite passed.

## Files Changed
- `deploy/ansible/deploy-secret-bundles.yml`: validate live bundle metadata on identical-generation retries.
- `src/test/java/auto/parto/config/AnsibleVaultDeploymentIT.java`: add healthy, missing-file, and wrong-mode retry scenarios.
- `.architecture-backlog/tickets/AP-0045-ansible-vault-vps-secrets.md`: record verified behavior and test evidence.

## Verification
- Focused Docker integration: `AnsibleVaultDeploymentIT`, 1 test passed, 0 skipped.
- Full `./mvnw -B verify`: BUILD SUCCESS; unit tests 413 run, 0 failures, 0 errors, 1 skipped; integration tests 148 run, 0 failures, 0 errors, 1 skipped; coverage checks passed.
- `git diff --check`: passed.

## Unresolved Risks
- No staging or VPS secret delivery/recovery drills are covered by this local integration test.
