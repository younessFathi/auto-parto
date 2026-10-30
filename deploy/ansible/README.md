# Ansible Vault secret delivery

This is the selected production design from [ADR-0001](../../docs/architecture-decisions/ADR-0001-production-secret-delivery.md). These playbooks are an implementation slice, not evidence that production hosts or secret custody have been configured. Keep actual inventory, encrypted Vault inputs, and deployment credentials in the separately controlled infrastructure/deployment repository; do not commit them to the application repository.

## Inventory contract

Define one `secret_bundle_targets` inventory group containing only allowlisted private hosts with verified SSH host keys. Every host declares `secret_target_role` and a non-empty `vault_bundles` list. Application hosts must declare exactly one `application-runtime` item; optional bundles may not duplicate a destination. Each item contains `source`, `role`, `gid`, and `generation_path`; destination paths are fixed by role and cannot be supplied freely. The deployment and migration-cleanup playbooks both select this group and use `secret_target_role` to target migration runners, so cleanup cannot silently miss the host that received the migration bundle. The playbook restricts each bundle role to its host role:

- `application`: `application-runtime`, and optional `audit-runtime`.
- `application_database`: `database-bootstrap` and/or `backup`.
- `audit_database`: `audit-admin`.
- `object_storage`: `object-storage-admin`.
- `migration_runner`: `migration` only.

Every bundle item has:

- `source`: absolute path to a role-scoped Ansible Vault encrypted properties file on the operator workstation.
- `role`: one of the permitted roles for the target.
- `gid`: numeric group ID for the consuming service. Both application bundles require GID `10001`, matching the production container's configured `10001:10001` identity. Other target GIDs must match their service process identity.
- `generation_path`: the exact role path from the allowlist below.

| Target role | Allowed bundle paths under its secret root |
| --- | --- |
| `application` | `secrets/secrets.properties` (`application-runtime`), `audit/secrets.properties` (`audit-runtime`) |
| `application_database` | `database/bootstrap.properties`, `backup/secrets.properties` |
| `audit_database` | `database/audit-admin.properties` |
| `object_storage` | `object-storage/admin.properties` |
| `migration_runner` | `migration/secrets.properties` |

The application secret root is `/run/auto-parto`. Compose uses long-form read-only bind syntax with `create_host_path: false` to mount `/run/auto-parto/active` at `/run/auto-parto` inside the container, preserving the Spring imports at `/run/auto-parto/secrets/secrets.properties` and `/run/auto-parto/audit/secrets.properties`. If first activation has not created the active link, Compose fails instead of creating a directory at that path. The active path is an atomic symlink to an immutable generation. Compose must recreate/restart the app after activation so Docker binds the new generation. Other target roles use their own fixed roots under `/run` and service-specific consumers. Never put database administration or migration credentials on the application host. Migration bundles belong only on the separately controlled migration runner and must be removed after the migration with `remove-migration-bundle.yml`.

The playbook validates numeric GIDs lexically before conversion, checks every encrypted source header before staging, decrypts the complete set into an inactive generation, and verifies each file's owner, numeric group, and mode before activation. An exclusive per-host/per-role lock covers source checks, staging, activation, and failure cleanup. A retry that finds the expected active generation still checks every declared file's type, UID, GID, and mode with checksum collection disabled; it reports only the role and relative path for invalid files and never enters staging cleanup. If any source fails decryption or validation during staging, cleanup rechecks the active link's absolute target and never removes a generation that is live. Activation validates the complete allowlisted path set and atomically replaces the symlink. Files are root-owned mode `0440`; diffs and secret-bearing task output are disabled. A crashed Ansible controller can leave the lock directory behind; an operator may remove it only after confirming no deployment or cleanup process remains on that host. The operator must create/secure each target's `/run` tmpfs. The app must remain stopped until the generation is active and the existing production configuration validation passes.

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

Deploy encrypted production and non-production inputs separately, using independent passwords. A missing, invalid, or plaintext source fails before activation. Generation staging never overwrites the active bundle; no plaintext backup copies are created. Rotation uses a replacement credential: install it, restart and verify readiness, reject the old credential, and remove old inactive generations after acceptance. Never roll back to a revoked credential; issue a fresh one instead.

Vault unlock-password custody and recovery must be configured independently from encrypted file backups. Assign an operator custodian and a separate recovery custodian, test both recovery paths in staging, and record results without values. The app host contains plaintext while running; host compromise requires credential revocation and replacement. These operational controls and staging drills remain AP-0045 acceptance work.
