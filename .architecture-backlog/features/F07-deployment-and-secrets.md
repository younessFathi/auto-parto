# F07 Deployment and Secrets

Deploy stateless application instances behind TLS with isolated configuration and protected secrets.

**Status:** In Progress

- [AP-0020](../tickets/AP-0020-stateless-deployment.md): Deploy bounded non-root application replicas.
- [AP-0021](../tickets/AP-0021-tls-entrypoint.md): Configure Caddy, trusted proxies, and SSE timeouts.
- [AP-0022](../tickets/AP-0022-openbao-secrets.md): Establish OpenBao-backed secret delivery and rotation. Repository templates and RS256/JWKS behavior are delivered; live infrastructure and drills remain outstanding.
- [AP-0044](../tickets/AP-0044-openbao-vps-go-live.md): Provision and verify the private OpenBao service and workload integration when production go-live is approved. A non-production dev-mode container is disposable and is not production evidence.
- [AP-0026](../tickets/AP-0026-data-infrastructure.md): Harden PostgreSQL and Redis production infrastructure.
- [AP-0028](../tickets/AP-0028-provider-isolation.md): Isolate blocking external provider clients.

## Completion Tracking

AP-0020 is complete. Its deployment increment establishes replica-safe application services, non-root image execution, bounded runtime resources, graceful shutdown, and local-only port publishing for development. `DeploymentComposeIT` builds and starts the canonical `springboot-postgresql-app` Compose service with the `postgres` profile in an isolated project and verifies the generated and running filesystem and resource constraints.

AP-0022 remains in progress. Its production profile imports required credentials from an OpenBao Agent rendered file; the app container does not receive the Agent token. The systemd app unit now requires the Agent and gates Compose startup on a complete, correctly permissioned bundle. The repository also includes TLS/Raft, policy, agent, Compose, and staging-verification templates, plus RS256/JWKS overlap/revocation behavior. These templates have not been applied to an actual service and no rotation, old-credential rejection, unseal, or restore drill is evidenced. F07 is not complete: TLS entrypoint work (AP-0021), PostgreSQL/Redis hardening and recovery (AP-0026), external-provider isolation (AP-0028), live OpenBao provisioning/drills, and remaining production controls are still required.

For the current readiness plan, complete AP-0022's operational secret-delivery prerequisites before AP-0026 and AP-0028. AP-0028 precedes AP-0021 because the proxy rollout and health-gated routing depend on known provider timeout and failure behavior. AP-0026 and AP-0028 can then proceed independently; AP-0021 follows AP-0028.
