# Ansible Vault secret delivery

This is the selected production design from [ADR-0001](../../docs/architecture-decisions/ADR-0001-production-secret-delivery.md). These playbooks are an implementation slice, not evidence that production hosts or secret custody have been configured. Keep actual inventory, encrypted Vault inputs, and deployment credentials in the separately controlled infrastructure/deployment repository; do not commit them to the application repository.

## Inventory contract

Define a `secret_bundle_targets` inventory group containing only allowlisted private hosts with verified SSH host keys. Each host declares `secret_target_role` and a non-empty `vault_bundles` list. The playbook restricts each bundle role to its host role:

- `application`: `application-runtime`, and optional `audit-runtime`.
- `application_database`: `database-bootstrap` and/or `backup`.
- `audit_database`: `audit-admin`.
- `object_storage`: `object-storage-admin`.
- `migration_runner`: `migration` only.

Every bundle item has:

- `source`: absolute path to a role-scoped Ansible Vault encrypted properties file on the operator workstation.
- `role`: one of the permitted roles for the target.
- `destination`: absolute path for the consuming host and service role.
- `group`: the least-privileged host group that can read that bundle.

Use `/run/auto-parto/secrets/secrets.properties` for the app bundle and `/run/auto-parto/audit/secrets.properties` for the optional audit bundle; both are mounted read-only into the application. Other roles must use their own host and file paths. Never put database administration or migration credentials on the application host. Migration bundles belong only on the separately controlled migration runner and must be removed after the migration with `remove-migration-bundle.yml`.

The playbook checks that every source has the Ansible Vault file header before using Ansible's automatic source decryption. It writes files as root-owned mode `0440`, disables diff, suppresses secret-bearing task output, and verifies destination metadata without reading contents. The operator must create/secure the target's `/run` tmpfs and ensure the named read group exists before deployment. The app must remain stopped until all required bundles are installed and the existing production configuration validation passes.

## Operator invocation

Run only from the separately controlled infrastructure/deployment repository on a trusted workstation after verifying the immutable application release, inventory, and SSH host keys. Unlock interactively; never pass the password as an argument or environment variable:

```sh
ANSIBLE_CONFIG=deploy/ansible/ansible.cfg ansible-playbook \
  --inventory inventory/production.yml \
  deploy/ansible/deploy-secret-bundles.yml \
  --ask-vault-pass --limit secret_bundle_targets
```

`--ask-vault-pass` prompts without echoing the password. Do not configure a vault password file, CI secret, shell history entry, debug callback, or verbose task output for production. Later automated unlock/retrieval requires a separate approved design.

## Lifecycle

Deploy encrypted production and non-production inputs separately, using independent passwords. A missing, invalid, or plaintext source fails before installation. Install files atomically and never create plaintext backup copies. Rotation uses a replacement credential: install it, restart and verify readiness, reject the old credential, and remove the old value. Never roll back to a revoked credential; issue a fresh one instead.

Vault unlock-password custody and recovery must be configured independently from encrypted file backups. Assign an operator custodian and a separate recovery custodian, test both recovery paths in staging, and record results without values. The app host contains plaintext while running; host compromise requires credential revocation and replacement. These operational controls and staging drills remain AP-0045 acceptance work.
