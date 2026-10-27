# AP-0022 Establish OpenBao Secret Management

- **Status:** In Progress
- **Type:** Technical Enhancement
- **Feature:** [Deployment and Secrets](../features/F07-deployment-and-secrets.md)
- **Linked Tickets:** [AP-0020](AP-0020-stateless-deployment.md), [AP-0044](AP-0044-openbao-vps-go-live.md)
- **Source:** `deployment-architecture.md` Secrets and Configuration

## Description
Deliver environment-isolated application credentials through protected OpenBao policies and controlled rotation, including deployment, workload identity, and recovery evidence. [AP-0044](AP-0044-openbao-vps-go-live.md) tracks the approved VPS deployment work that will produce part of the operational evidence; linking that work does not change this ticket's status or acceptance criteria.

## Current Implementation Slice

The production application profile requires protected PostgreSQL/R2DBC, Redis, JWT, object-storage, and OpenBao configuration, validates enabled email/Firebase/audit credentials, rejects loopback dependency endpoints, requires PostgreSQL cutover flags, and excludes MongoDB runtime configuration. This ticket delivers required Agent-rendered properties, an app Compose boundary, a digest-pinned normal-mode OpenBao image Compose template for a dedicated TLS/Raft host, a systemd lifecycle, separated policies/Agent templates, repeatable staging scripts, and RS256/JWKS overlap/revocation behavior.

The local OpenBao image profile is loopback-only, in-memory, and explicitly disposable. `OpenBaoImageDeploymentIT` exercises the production image configuration with temporary TLS and persistent test storage, initializes/unseals the isolated server, tests AppRole policy isolation, rotates/revokes a SecretID, renews/revokes workload tokens, and verifies state recovery after restart. It uses only generated test credentials and removes its test infrastructure. The test validates the repository deployment and automation contract; it does not represent a deployed VPS or production secret service.

The repository and image-based implementation increment is complete, but AP-0022 remains In Progress. No external OpenBao service, production workload bindings, live database/provider credential rotation and old-value rejection, or backup/snapshot restore and quorum unseal drills have been verified. AP-0044 tracks the VPS work and evidence for these outstanding acceptance criteria; F09 remains a go-live gate.

### Acceptance Evidence Snapshot (2026-10-26)

| Criterion | Evidence in this change | Status / remaining proof |
| --- | --- | --- |
| 1. Externalize required credentials | Required production import, audit-specific optional bundle, enabled provider checks, and Agent templates for database, Redis, S3, JWT, email, Firebase, and audit values. | Repository contract is implemented; verify provisioned values and fail-closed application startup with deployed Agents in the approved environment (AP-0044). |
| 2. Short-lived identity and least privilege | Scoped app/audit/migration policies, idempotent AppRole/KV/database mount installer, wrapped one-use SecretIDs, and bounded token TTLs. `OpenBaoImageDeploymentIT` verifies app access and cross-path denial. | Repository policy behavior is tested; actual environment identity bindings, dynamic leases, renewal, and revocation remain unverified (AP-0044). |
| 3. Fail safely when secrets are absent | Required properties import, production validation, systemd bundle gate, and isolated missing/incomplete-bundle tests. | Local configuration evidence exists; production workload startup and delayed Agent behavior remain unverified (AP-0044). |
| 4. Private TLS service and recovery | Digest-pinned production image Compose template, private binds, TLS/config read-only mounts, external Raft volume, systemd lifecycle, and health probe. `OpenBaoImageDeploymentIT` exercises normal mode with TLS and persistent storage. | The image test exposed an unreadable ephemeral private key: OpenSSL created it as host-owned mode 0600 while the image runs as unprivileged `openbao`. The fixture now makes its temporary TLS files readable and emits startup logs/health details on failure, but Docker was unavailable for a post-fix integration rerun. The approved private service, protected backups, snapshot restore, and unseal/quorum recovery are not deployed or drilled (AP-0044). |
| 5. Rotation/recovery | Staging authorization/lease scripts, integration coverage for SecretID and token renewal/revocation, persistent-volume restart recovery, and documented snapshot/restore procedure. | Test identities and process restart are exercised; production database/provider credential rotation, old-value rejection, rollback, and backup restore drills remain outstanding (AP-0044). |
| 6. Environment separation | Local HS256/test config, disposable local OpenBao dev profile, and production required external RS256 bundle/policies. Compose and profile tests enforce the boundaries. | Repository profile/policy boundaries are tested; real staging/production identity separation remains unverified (AP-0044). |
| 7. Asymmetric rotating JWT | RS256 signing with `kid`, public JWKS, overlap/removal behavior, and unit/HTTP tests. | Repository behavior is tested; deployment rollover and revocation rehearsal remains outstanding (AP-0044). |

AP-0022 remains **In Progress** until every acceptance criterion is verified, including the actual protected secret service, production workload bindings, credential rotation and old-value rejection, and backup/restore/unseal recovery. AP-0044 tracks approved VPS provisioning and operational evidence; it does not replace or close AP-0022.

### OpenBao image startup health-check follow-up (2026-10-27)

The reported unhealthy container is consistent with the integration fixture's TLS key permissions: `openssl req -keyout` creates a host-owned 0600 key, while the pinned official image runs as the non-root `openbao` user. The test now sets temporary fixture directory/key permissions for container readability and collects Compose logs plus health state when startup still fails. Production TLS key ownership/readability is documented in the secrets runbook. The corrected image integration test could not be rerun here because Testcontainers stalled initializing Docker Desktop; this is a likely root cause supported by the fixture and upstream image user configuration, not a post-fix container pass claim.

## Acceptance Criteria
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

## Definition of Done
The protected secret service and workload integrations are deployed in the approved environment; every acceptance criterion and test scenario, including actual rotation/old-value rejection and backup/restore/unseal drills, has recorded evidence; runbooks and ownership are operational; and focused tests plus `./mvnw -B test` and `./mvnw -B verify` pass. AP-0044 provides deployment evidence for this ticket but does not waive any acceptance criterion.
