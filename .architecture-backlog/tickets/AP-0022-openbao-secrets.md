# AP-0022 Establish OpenBao Secret Management

- **Status:** In Progress
- **Type:** Technical Enhancement
- **Feature:** [Deployment and Secrets](../features/F07-deployment-and-secrets.md)
- **Linked Tickets:** [AP-0020](AP-0020-stateless-deployment.md)
- **Source:** `deployment-architecture.md` Secrets and Configuration

## Description
Deliver environment-isolated application credentials through protected OpenBao policies and controlled rotation.

## Current Implementation Slice

The application-side production validation increment requires protected PostgreSQL/R2DBC, Redis, JWT, object-storage, and OpenBao configuration, validates enabled email/Firebase/audit credentials, rejects loopback dependency endpoints, requires the PostgreSQL-only cutover flags, and excludes MongoDB runtime configuration. This change adds a required OpenBao Agent rendered-properties import, a production app Compose boundary, a digest-pinned OpenBao image Compose template for a dedicated TLS/Raft host, a systemd lifecycle, separated policy/agent templates, a staging authorization script, and RS256/JWKS overlap and revocation support with isolated tests. `docker-compose.openbao-dev.yml` supplies an isolated loopback-only disposable OpenBao image for non-production exploration; it is not wired to the application or treated as production identity/persistence/recovery evidence. Compose smoke verification uses only safe test values explicitly through its test harness; it does not use production fallbacks.

No external OpenBao service, role bindings, dynamic database plugin, lease renewal/revocation exercise, rotation, backup/restore, or unseal drill has been run. This ticket therefore remains **In Progress**, and this code is not operational proof of the secret service.

### Acceptance Evidence Snapshot (2026-10-26)

| Criterion | Evidence in this change | Status / remaining proof |
| --- | --- | --- |
| 1. Externalize required credentials | Required prod properties import from `/run/auto-parto/secrets/secrets.properties`; audit has a separate optional file whose missing values fail validation when enabled; provider keys are in the app policy path. | Repository contract present; exercise full prod startup with provisioned secrets in staging. |
| 2. Short-lived identity and least privilege | Separate app/audit/migration policies, AppRole Agent templates, wrapped one-use SecretID settings, short token TTLs and scoped policy installer. | Templates only; bind actual roles and verify lease renewal/revocation and cross-environment denial in OpenBao staging. |
| 3. Fail safely when secrets are absent | Required properties file import, `ProductionConfigValidation`, and the systemd pre-start credential gate. The gate checks file type, group/mode, required keys, enabled provider/audit credentials, and RS256 before Compose starts. | Isolated gate/config tests cover missing and incomplete bundles; verify a real prod-profile startup against the deployed Agent in staging. |
| 4. Private TLS service and recovery | Digest-pinned OpenBao image Compose deployment for a dedicated host; private host binds; read-only TLS/config mounts; external persistent Raft volume; systemd lifecycle; TLS health probe and encrypted snapshot/unseal procedure. | Compose configuration and local normal-mode container smoke pass; not complete until the separate HA service is provisioned and restore/unseal drill records RPO/RTO. |
| 5. Rotation/recovery | Agent render/restart hook, credential/JWT rotation sequence, staging authorization harness, and RSA overlap/revocation unit coverage. | No external credential was rotated, revoked, or rejected; no snapshot was restored. Staging drills remain release blockers. |
| 6. Environment separation | Local HMAC defaults remain local/test; `docker-compose.openbao-dev.yml` provides a separate loopback-only, in-memory dev image profile; prod requires the external rendered bundle and RS256 material; distinct staging and production policies are supplied. | Dev container is only an exploration sandbox, not application workload-identity or production evidence. Confirm separation with actual staging identities and production startup rehearsal. |
| 7. Asymmetric rotating JWT | RS256 signing with `kid`, JWKS endpoint, overlapping verification set; tests verify old and active keys during overlap and reject removed key afterward. | Code behavior tested; publish/rollover/revocation rehearsal against the deployment remains outstanding. |

The whole ticket remains **In Progress**. OpenBao provisioning, TLS and least-privilege policies, short-lived workload identity, dynamic database leases, rotation/revocation drills, backups/unseal recovery, and asymmetric JWT key overlap are infrastructure and operations follow-ups. They must be delivered with AP-0021, AP-0026, AP-0028, and the remaining F07 work before production readiness is claimed.

## Acceptance Criteria
1. Required database, Redis, JWT, OAuth, Firebase, email, and storage secrets are externalized.
2. Workloads authenticate with short-lived machine identity and least-privilege policies.
3. Production fails safely when required secrets are missing and never falls back to development values.
4. OpenBao is private, TLS-protected, backed up, and outside the database failure boundary.
5. Rotation and recovery procedures are documented and tested.
6. Local uses safe optional defaults, test uses isolated fake/container providers, and production requires validated external secrets.
7. JWT signing prefers asymmetric rotating keys with published verification keys and a tested overlap window.

## Test Scenarios
1. **Given** a production workload, **when** it requests another environment's secret, **then** access is denied.
2. **Given** a missing required secret, **when** production starts, **then** startup fails safely.
3. **Given** a rotated credential, **when** rollout completes, **then** the old credential is revoked safely.
4. **Given** JWT key rotation, **when** the overlap window is active, **then** new and allowed prior tokens verify through published keys.

## Definition of Done
The whole ticket is implemented, every acceptance criterion is satisfied, every scenario is automated and passing, secret rotation and recovery runbooks are complete, and all applicable suites pass.
