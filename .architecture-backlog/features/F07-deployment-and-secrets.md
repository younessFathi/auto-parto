# F07 Deployment and Secrets

Deploy stateless application instances behind TLS with isolated configuration and protected secrets.

**Status:** In Progress

- [AP-0020](../tickets/AP-0020-stateless-deployment.md): Deploy bounded non-root application replicas.
- [AP-0021](../tickets/AP-0021-tls-entrypoint.md): Configure Caddy, trusted proxies, and SSE timeouts.
- [AP-0022](../tickets/AP-0022-openbao-secrets.md): Establish OpenBao-backed secret delivery and rotation. Repository/image configuration, policy templates, fail-closed app delivery, and Docker integration evidence are complete; live VPS deployment and drills are [AP-0044](../tickets/AP-0044-openbao-vps-go-live.md).
- [AP-0044](../tickets/AP-0044-openbao-vps-go-live.md): Provision and verify the private OpenBao service and workload integration when production go-live is approved. A non-production dev-mode container is disposable and is not production evidence.
- [AP-0026](../tickets/AP-0026-data-infrastructure.md): Harden PostgreSQL and Redis production infrastructure.
- [AP-0028](../tickets/AP-0028-provider-isolation.md): Isolate blocking external provider clients.

## Completion Tracking

AP-0020 is complete. Its deployment increment establishes replica-safe application services, non-root image execution, bounded runtime resources, graceful shutdown, and local-only port publishing for development. `DeploymentComposeIT` builds and starts the canonical `springboot-postgresql-app` Compose service with the `postgres` profile in an isolated project and verifies the generated and running filesystem and resource constraints.

AP-0022's repository and image-configuration scope is Done. Its production profile imports required credentials from an OpenBao Agent rendered file; the app container does not receive the Agent token. The systemd app unit requires the Agent and gates Compose startup on a complete, correctly permissioned bundle. The repository includes a digest-pinned normal-mode OpenBao image Compose template with TLS/Raft, private binds, persistent external volume, systemd lifecycle, policies, Agents, staging-verification scripts, and RS256/JWKS overlap/revocation behavior. `OpenBaoImageDeploymentIT` verifies TLS startup, policy isolation, workload identity rotation/revocation, and restart recovery. No actual VPS service, production credential rotation, quorum unseal, or snapshot restore has been exercised; those belong to AP-0044. F07 is not complete: TLS entrypoint work (AP-0021), PostgreSQL/Redis hardening and recovery (AP-0026), external-provider isolation (AP-0028), live OpenBao provisioning/drills, and remaining production controls are still required.

`docker-compose.openbao-dev.yml` is a separate local-only sandbox using the official OpenBao image in memory-only dev mode, published on loopback and with a throwaway dev token. It is not wired into the application production profile, is not suitable for real credentials, and does not satisfy AP-0022's live identity, persistence, or recovery requirements. Production VPS/service work is tracked separately by proposed AP-0044.

For implementation readiness, AP-0022's repository secret-delivery contract is complete, so AP-0026 and AP-0028 can proceed with isolated test credentials and providers. AP-0028 precedes AP-0021 because the proxy rollout and health-gated routing depend on known provider timeout and failure behavior. AP-0026 and AP-0028 can proceed independently; AP-0021 follows AP-0028. AP-0044 provisions the actual production OpenBao service and executes live operations drills before go-live.
