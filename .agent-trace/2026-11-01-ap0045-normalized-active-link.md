# Prompt Trace: AP-0045 normalized active link

## Request Summary
Fix AP-0045 retry identity so equivalent absolute active symlinks cannot cause staging writes into the live generation, and verify the behavior with the real Ansible playbook.

## Work Performed
- Resolve the active symlink to a normalized absolute path once under the existing per-root lock.
- Reuse the resulting active-generation identity for retry metadata checks and the staging decision.
- Extend the real-playbook Testcontainers regression for healthy absolute-link retry, missing application-file retry, and mode `0644` retry, checking active target/file metadata, audit readability, and lock release.
- Change failed-decryption coverage to use a changed encrypted application bundle and a distinct inactive generation.
- Preserve relative-link, inventory/GID, role separation, locking, Compose, and migration-cleanup scenarios.
- Updated the Ansible README and AP-0045 tracking. AP-0045 remains In Progress; AP-0046 remains Proposed.

## Result
Equivalent relative and absolute symlinks now follow the same read-only validation path. The focused real-playbook test and full verification suite passed.

## Files Changed
- `deploy/ansible/deploy-secret-bundles.yml`: normalized absolute active-target identity and shared retry/staging guard.
- `src/test/java/auto/parto/config/AnsibleVaultDeploymentIT.java`: absolute-link retry and distinct failed-decryption coverage.
- `deploy/ansible/README.md`: documented active-link identity behavior.
- `.architecture-backlog/tickets/AP-0045-ansible-vault-vps-secrets.md`: recorded regression evidence.

## Verification
- Focused Docker integration `AnsibleVaultDeploymentIT`: 1 test passed, 0 skipped.
- Full `./mvnw -B verify`: BUILD SUCCESS; unit tests 413 run, 0 failures, 0 errors, 1 skipped; integration tests 148 run, 0 failures, 0 errors, 1 skipped; coverage checks passed.
- `git diff --check`: passed.

## Unresolved Risks
- Provider-host staging, password custody/recovery, rotation, old-credential rejection, and host recovery remain AP-0045 operational gates.
