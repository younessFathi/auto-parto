# AP-0022 Establish OpenBao Secret Management

- **Status:** Cancelled
- **Type:** Technical Enhancement
- **Feature:** [Deployment and Secrets](../features/F07-deployment-and-secrets.md)
- **Linked Tickets:** [AP-0020](AP-0020-stateless-deployment.md), [AP-0044](AP-0044-openbao-vps-go-live.md), [AP-0045 Secret Delivery Decision](AP-0045-ansible-vault-vps-secrets.md), [AP-0046 VPS Infrastructure Automation](AP-0046-provision-vps-topology-with-opentofu-ansible.md)
- **Source:** `deployment-architecture.md` Secrets and Configuration

## Description
The OpenBao production secret-delivery path is cancelled/superseded by [ADR-0001](../../docs/architecture-decisions/ADR-0001-production-secret-delivery.md), which selects operator-controlled Ansible Vault injection. The repository's OpenBao image and Agent work remains historical prototype/development material and does not satisfy production secret delivery. [AP-0045](AP-0045-ansible-vault-vps-secrets.md) owns implementation of the selected model. AP-0022 is not Done.

The acceptance evidence and criteria below are retained as historical evidence for the superseded OpenBao implementation; they are no longer active production acceptance criteria. Do not use OpenBao to deliver any initial production credential. AP-0045 must replace OpenBao-specific production requirements with the selected fail-closed file contract.

## Historical Prototype Implementation

At implementation time, the production application profile was configured to require protected PostgreSQL/R2DBC, Redis, JWT, object-storage, and OpenBao settings, validate enabled email/Firebase/audit credentials, reject loopback endpoints, require PostgreSQL cutover flags, and exclude MongoDB. The prototype added Agent-rendered properties, Compose templates, a normal-mode OpenBao image template, systemd lifecycle, policies/Agent templates, staging scripts, and RS256/JWKS overlap/revocation behavior. That OpenBao-specific production contract is superseded and must be removed/replaced under AP-0045; current runtime behavior does not yet match ADR-0001.

The local OpenBao image profile is loopback-only, in-memory, and explicitly disposable. `OpenBaoImageDeploymentIT` exercises the production image configuration with temporary TLS and persistent test storage, initializes/unseals the isolated server, tests AppRole policy isolation, rotates/revokes a SecretID, renews/revokes workload tokens, and verifies state recovery after restart. It uses only generated test credentials and removes its test infrastructure. The test validates the repository deployment and automation contract; it does not represent a deployed VPS or production secret service.

The repository/image prototype increment was implemented, but no production OpenBao service, production workload bindings, live credential rotation and old-value rejection, or backup/snapshot restore and quorum unseal drills were verified. ADR-0001 supersedes the OpenBao production path; AP-0022 is Cancelled, not Done. AP-0045 owns the selected Ansible Vault implementation, and F09 remains a go-live gate.

### Acceptance Evidence Snapshot (2026-10-26)

| Criterion | Evidence in this change | Status / remaining proof |
| --- | --- | --- |
| 1. Externalize required credentials | Historical required production import, audit-specific optional bundle, enabled provider checks, and Agent templates for database, Redis, S3, JWT, email, Firebase, and audit values. | OpenBao-specific import is superseded; AP-0045 must implement and test the selected Vault-mounted files. |
| 2. Short-lived identity and least privilege | Historical scoped app/audit/migration policies, AppRole/KV/database installer, wrapped SecretIDs, and token TTLs. `OpenBaoImageDeploymentIT` exercises prototype policy isolation. | Not required by the selected initial model; AP-0045 enforces role-scoped deploy-time bundles and records the accepted lack of leases. |
| 3. Fail safely when secrets are absent | Historical required properties import, production validation, systemd bundle gate, and isolated missing/incomplete-bundle tests. | AP-0045 must replace OpenBao-specific validation and prove startup fails closed for missing/incomplete Vault-mounted bundles. |
| 4. Private TLS service and recovery | Historical digest-pinned image template, TLS/Raft config, systemd lifecycle, and health probe. | Superseded; no production OpenBao service will be provisioned under ADR-0001. |
| 5. Rotation/recovery | Historical staging authorization/lease scripts, token renewal/revocation tests, and restart persistence coverage. | AP-0045 must demonstrate manual secret rotation, old-value rejection, Vault/password recovery, and host rebuild; no such staging drill has passed yet. |
| 6. Environment separation | Historical local/test and production OpenBao profile/policy checks. | AP-0045 must verify environment and workload-role separation in its selected file-delivery path. |
| 7. Asymmetric rotating JWT | RS256 signing with `kid`, public JWKS, overlap/removal behavior, and unit/HTTP tests. | Repository behavior is tested; AP-0045 must run the deployment rollover and revocation rehearsal. |

ADR-0001 retires the OpenBao production criteria in this ticket. AP-0022 is Cancelled, not Done; AP-0045 implements the accepted Ansible Vault path, and AP-0046 reflects its four-VPS topology while remaining Proposed pending infrastructure prerequisites.

### OpenBao image startup health-check follow-up (2026-10-27)

The reported unhealthy container is consistent with the integration fixture's TLS key permissions: `openssl req -keyout` creates a host-owned 0600 key, while the pinned official image runs as the non-root `openbao` user. The test now sets temporary fixture directory/key permissions for container readability and collects Compose logs plus health state when startup still fails. Production TLS key ownership/readability is documented in the secrets runbook. The corrected image integration test could not be rerun here because Testcontainers stalled initializing Docker Desktop; this is a likely root cause supported by the fixture and upstream image user configuration, not a post-fix container pass claim.

## Historical Acceptance Criteria (Not Active After ADR-0001)
1. Required database, Redis, JWT, OAuth, Firebase, email, storage, and audit secrets are externalized and delivered to enabled workloads through protected bundles.
2. Workloads authenticate with short-lived machine identity and least-privilege, environment/role-scoped policies; lease renewal and revocation are verified.
3. Production fails safely when required secrets are missing and never falls back to development values.
4. OpenBao is private, TLS-protected, backed up, and outside the application/audit database failure boundary; restore and unseal recovery are exercised.
5. Credential rotation, rollback, revocation, and old-credential rejection are documented and tested against the deployed service.
6. Local uses safe optional defaults, tests use isolated fake/container providers, and production requires validated external secrets.
7. JWT signing uses asymmetric rotating keys with a published verification-key set and a tested overlap/revocation window.

## Test Scenarios
1. **Given** a production workload, **when** it requests another environment's secret, **then** access is denied.
2. **Given** a missing required secret, **when** production starts, **then** startup fails safely.
3. **Given** a rotated credential, **when** rollout completes, **then** the old credential is revoked safely.
4. **Given** JWT key rotation, **when** the overlap window is active, **then** new and allowed prior tokens verify through published keys.
5. **Given** the pinned normal-mode image and temporary TLS material, **when** the Docker integration test initializes it, **then** AppRole access stays within its policy, old SecretIDs and revoked tokens fail, renewed tokens work, and the external test volume retains state across restart.

## Historical Definition of Done (Superseded)
Not applicable: AP-0022's OpenBao production scope was cancelled by ADR-0001 before deployment. Do not interpret the repository prototype or tests as production secret-delivery evidence.
