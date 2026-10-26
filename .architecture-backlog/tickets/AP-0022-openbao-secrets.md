# AP-0022 Establish OpenBao Secret Management

- **Status:** Done
- **Type:** Technical Enhancement
- **Feature:** [Deployment and Secrets](../features/F07-deployment-and-secrets.md)
- **Linked Tickets:** [AP-0020](AP-0020-stateless-deployment.md), [AP-0044](AP-0044-openbao-vps-go-live.md)
- **Source:** `deployment-architecture.md` Secrets and Configuration

## Description
Deliver the repository's container-based OpenBao deployment configuration, environment-isolated credential policies, production Agent integration, and verifiable local/staging automation. Actual VPS provisioning and live production operations are tracked by [AP-0044](AP-0044-openbao-vps-go-live.md).

## Current Implementation Slice

The production application profile requires protected PostgreSQL/R2DBC, Redis, JWT, object-storage, and OpenBao configuration, validates enabled email/Firebase/audit credentials, rejects loopback dependency endpoints, requires PostgreSQL cutover flags, and excludes MongoDB runtime configuration. This ticket delivers required Agent-rendered properties, an app Compose boundary, a digest-pinned normal-mode OpenBao image Compose template for a dedicated TLS/Raft host, a systemd lifecycle, separated policies/Agent templates, repeatable staging scripts, and RS256/JWKS overlap/revocation behavior.

The local OpenBao image profile is loopback-only, in-memory, and explicitly disposable. `OpenBaoImageDeploymentIT` exercises the production image configuration with temporary TLS and persistent test storage, initializes/unseals the isolated server, tests AppRole policy isolation, rotates/revokes a SecretID, renews/revokes workload tokens, and verifies state recovery after restart. It uses only generated test credentials and removes its test infrastructure. The test validates the repository deployment and automation contract; it does not represent a deployed VPS or production secret service.

AP-0022's repository deliverable is complete. Provisioning the external service, establishing real workload bindings, live database/provider credential rotation and old-value rejection, and backup/snapshot restore and quorum unseal drills remain release gates tracked in AP-0044 and F09.

### Acceptance Evidence Snapshot (2026-10-26)

| Criterion | Evidence in this change | Status / remaining proof |
| --- | --- | --- |
| 1. Externalize required credentials | Required production import, audit-specific optional bundle, enabled provider checks, and Agent templates for database, Redis, S3, JWT, email, Firebase, and audit values. | Repository contract complete; real secret provisioning is AP-0044. |
| 2. Short-lived identity and least privilege | Scoped app/audit/migration policies, idempotent AppRole/KV/database mount installer, wrapped one-use SecretIDs, and bounded token TTLs. `OpenBaoImageDeploymentIT` verifies app access and cross-path denial. | Repository contract complete; bind actual environment identities in AP-0044. |
| 3. Fail safely when secrets are absent | Required properties import, production validation, systemd bundle gate, and isolated missing/incomplete-bundle tests. | Repository contract complete; production host startup rehearsal is AP-0044. |
| 4. Private TLS service and recovery configuration | Digest-pinned production image Compose template, private binds, TLS/config read-only mounts, external Raft volume, systemd lifecycle, and health probe. `OpenBaoImageDeploymentIT` starts normal mode with verified TLS and persistent storage. | Repository config complete; deploy HA service and record backup/restore/unseal RPO/RTO in AP-0044. |
| 5. Rotation/recovery automation | Staging authorization/lease scripts, integration coverage for SecretID and token renewal/revocation, persistent-volume restart recovery, and documented snapshot/restore procedure. | Repository automation complete; real credential rotation and snapshot restore drills are AP-0044. |
| 6. Environment separation | Local HS256/test config, disposable local OpenBao dev profile, and production required external RS256 bundle/policies. Compose and profile tests enforce the boundaries. | Repository contract complete; production identity binding is AP-0044. |
| 7. Asymmetric rotating JWT | RS256 signing with `kid`, public JWKS, overlap/removal behavior, and unit/HTTP tests. | Repository behavior complete; deployment rollover rehearsal is AP-0044. |

AP-0022 is **Done** for the repository and image-based configuration scope. Its completion does not claim a live production OpenBao service or make F07/F09 complete. AP-0044 owns VPS provisioning, actual workload bindings, live credential rejection, and backup/restore/quorum-unseal evidence.

## Acceptance Criteria
1. Production configuration externalizes database, Redis, JWT, OAuth, Firebase, email, object-storage, and audit credentials through required rendered bundles.
2. Repository policies and Agents define short-lived, environment/role-scoped application, audit, and migration identities; the image integration test verifies policy isolation, SecretID replacement, token renewal, and revocation.
3. Production startup fails closed on missing configuration and never uses development defaults; unit/config/systemd tests cover these failure modes.
4. A digest-pinned normal-mode OpenBao image deployment template defines TLS, private binds, Raft storage, persistent external volume, systemd lifecycle, and health probing; a Docker integration test starts it with temporary TLS and persistent test storage.
5. Rotation, restart recovery, snapshot restore, and unseal procedures are documented. The local integration test verifies SecretID/token rotation and restart recovery; external snapshot/restore and unseal execution is assigned to AP-0044.
6. Local/test/production profiles are separated and tested; the OpenBao dev image remains disposable and isolated.
7. Production JWT uses RS256 and published JWKS with tested overlap and removal behavior.

## Test Scenarios
1. **Given** a production workload, **when** it requests another environment's secret, **then** access is denied.
2. **Given** a missing required secret, **when** production starts, **then** startup fails safely.
3. **Given** a rotated credential, **when** rollout completes, **then** the old credential is revoked safely.
4. **Given** JWT key rotation, **when** the overlap window is active, **then** new and allowed prior tokens verify through published keys.
5. **Given** the pinned normal-mode image and temporary TLS material, **when** the Docker integration test initializes it, **then** AppRole access stays within its policy, old SecretIDs and revoked tokens fail, renewed tokens work, and the external test volume retains state across restart.

## Definition of Done
All repository and Docker image configuration criteria above are implemented and verified by focused tests plus `./mvnw -B test` and `./mvnw -B verify`. The real VPS deployment and externally executed recovery/rotation drills are AP-0044 go-live acceptance and do not block completion of this repository-scope ticket.
