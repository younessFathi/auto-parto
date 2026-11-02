# Prompt Trace: AP-0045 staging checklist

## Request Summary
Prepare a clear step-by-step staging checklist for the Ansible Vault deployment and explain what can be prepared before a VPS is available versus what requires the host.

## Work Performed
- Added a plain-language staging sequence covering pre-arrival preparation, SSH host-key verification, private networking, host role identities, Ansible checks, role-by-role secret delivery, app readiness, credential rotation/rejection, and recovery.
- Clarified safe diagnostics and stop conditions so operators do not expose credential contents while troubleshooting.
- Corrected the runbook overview to distinguish disposable Docker playbook tests from provider-host validation.
- Updated AP-0045 with a linkable tracking note while retaining In Progress status.

## Result
The runbook now identifies work that can be prepared now, steps to perform when a VPS arrives, what each step proves, and which staging drills must pass before AP-0045 can be reconsidered for Done.

## Files Changed
- `docs/production-secrets-runbook.md`: added the stepwise staging checklist and corrected evidence statement.
- `.architecture-backlog/tickets/AP-0045-ansible-vault-vps-secrets.md`: recorded checklist preparation without claiming staging evidence.

## Verification
- `git diff --check`: passed.
- No runtime code or tests changed.

## Unresolved Risks
- Provider host access is still pending; deployment, rotation, password recovery, and host recovery drills have not run.
