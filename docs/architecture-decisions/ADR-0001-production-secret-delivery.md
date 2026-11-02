# ADR-0001: Initial production secret delivery

- **Status:** Accepted
- **Date:** 2026-10-30
- **Decision owner:** Product owner
- **Related tickets:** [AP-0022](../../.architecture-backlog/tickets/AP-0022-openbao-secrets.md), [AP-0044](../../.architecture-backlog/tickets/AP-0044-openbao-vps-go-live.md), [AP-0045](../../.architecture-backlog/tickets/AP-0045-ansible-vault-vps-secrets.md), [AP-0046](../../.architecture-backlog/tickets/AP-0046-provision-vps-topology-with-opentofu-ansible.md)

## Context

The initial production plan used OpenBao as a protected runtime secret service. Its production model adds a separate service boundary, operator/unseal procedures, workload identity, lease and renewal operations, and recovery drills. The product owner selected Ansible Vault for the initial Contabo deployment to reduce the first deployment's operational moving parts. This decision changes the planned production delivery model; it does not claim that Ansible Vault automation, VPS infrastructure, or recovery drills already exist.

## Decision

Use **Ansible Vault with operator-controlled deploy-time injection** as the single source for initial production credentials. Do not use OpenBao or another runtime secret service to deliver the same production credentials. Reconsider this choice only through a superseding approved ADR.

An authorized operator will run a reviewed Ansible playbook from a trusted workstation and a separately controlled infrastructure/deployment repository. Ansible connects only to allowlisted hosts using verified SSH host keys. Encrypted inputs are partitioned by environment and workload role. The playbook installs only each role's required secret bundle with restrictive ownership/mode and the app mounts its bundle read-only. Migration credentials are present only for the migration operation and are removed afterward.

Unlock the Vault interactively. Keep the Vault password in an access-controlled password manager separate from the encrypted Vault data; assign an operator custodian and an independent recovery custodian before staging deployment. Never store the password in Git, on any VPS, in a command line, logs, traces, or ordinary CI secrets. Automated CI decryption is out of scope; any future automation requires a separate approved secure unlock/retrieval design. Password recovery must be exercised without placing a recovery copy beside the encrypted Vault data.

Keep separate production and non-production Vault passwords and files. Back up only encrypted Vault inputs through the protected infrastructure repository and an encrypted off-provider backup. Test recovery of the encrypted inputs and the password through their independent custody paths. Do not retain decrypted bundles in backups or repository artifacts.

## Consequences and accepted limitations

- Secret delivery is an operator-run deployment action. Rotation and revocation require issuing a replacement at the dependency, deploying it, verifying readiness, and rejecting the old credential; there are no dynamic leases or automatic workload renewal.
- Secret values exist in plaintext on the destination host and in the consuming process/container while in use. Host root compromise can expose them. AP-0045 must enforce host hardening, encrypted storage/backups where available, least-privilege access, incident response, and measured recovery/rotation drills.
- Rollback must never restore a revoked credential. If a credential is revoked, rollback requires issuing a new credential.
- Keep RS256 signing private keys inside the encrypted role-scoped Vault input; continue publishing only the public JWKS and testing overlap/removal behavior.
- The four-VPS topology in AP-0046 is compatible with this decision and needs no OpenBao host. Contabo region, account owner, complete cost, private-network setup, and OpenTofu provider lifecycle validation remain open before infrastructure readiness.
- The state-changing deployment/migration control remains outside ordinary application-repository workflows. GitLab remains active until a separately controlled protected path has successful end-to-end staging evidence.

## Required tracking changes

- AP-0045 is the implementation ticket for the selected Ansible Vault path.
- AP-0044's initial OpenBao VPS delivery is cancelled. Do not provision OpenBao to deliver the initial production credentials.
- AP-0022's OpenBao production acceptance path is cancelled/superseded; its repository implementation remains prototype/development material until AP-0045 removes OpenBao production requirements and establishes the selected fail-closed delivery path. AP-0022 is not Done.
- AP-0046 retains the four VPS roles and stays Proposed until its provider, account, cost, private-network, API/provider validation, and separate runner prerequisites are evidenced.
- F07 and F09 remain incomplete; this ADR is an architecture decision, not deployment or recovery evidence.
