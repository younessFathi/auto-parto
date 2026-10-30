# Prompt Trace: AP-0045 role isolation follow-up

## Request Summary
Continue AP-0045 local verification by exercising the real Ansible playbook across each declared secret target role and reconciling deployment documentation with current test evidence.

## Work Performed
- Extended the disposable Ansible integration fixture with encrypted test-only database-bootstrap, backup, audit-admin, and object-storage bundles.
- Executed application database, audit database, and object-storage inventories through the actual playbook and checked their fixed active-generation paths and contents.
- Corrected the production secrets runbook's outdated statement that Ansible had not been executed, and documented identical-generation validation behavior.
- Updated AP-0045 with the added role-destination evidence while preserving its In Progress status and provider-host operational gates.

## Result
Application, application-database/backup, audit-database, object-storage, and migration target roles pass disposable real-playbook coverage. This does not constitute Contabo staging or production deployment evidence.

## Files Changed
- `src/test/java/auto/parto/config/AnsibleVaultDeploymentIT.java`: added role-scoped bundle fixtures and delivery checks.
- `deploy/ansible/README.md`: documented active bundle validation on identical retries.
- `docs/production-secrets-runbook.md`: corrected the local Ansible verification statement and retained operator drill limitations.
- `.architecture-backlog/tickets/AP-0045-ansible-vault-vps-secrets.md`: recorded role-root evidence and limitations.

## Verification
- Focused Docker integration `AnsibleVaultDeploymentIT`: 1 test passed, 0 skipped.
- Full `./mvnw -B verify`: BUILD SUCCESS; unit tests 413 run, 0 failures, 0 errors, 1 skipped; integration tests 148 run, 0 failures, 0 errors, 1 skipped; coverage checks passed.
- `git diff --check`: passed.

## Unresolved Risks
- No provider-host staging, password custody/recovery, secret rotation, old-credential rejection, or host recovery drill has been performed.
