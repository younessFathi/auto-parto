# F07 Deployment and Secrets

Deploy stateless application instances behind TLS with isolated configuration and protected secrets.

**Status:** In Progress

- [AP-0020](../tickets/AP-0020-stateless-deployment.md): Deploy bounded non-root application replicas.
- [AP-0021](../tickets/AP-0021-tls-entrypoint.md): Configure Caddy, trusted proxies, and SSE timeouts.
- [AP-0022](../tickets/AP-0022-openbao-secrets.md): Establish OpenBao-backed secret delivery and rotation. The application validation increment is in progress; OpenBao infrastructure, policies, leases, rotation, and recovery remain outstanding.
- [AP-0026](../tickets/AP-0026-data-infrastructure.md): Harden PostgreSQL and Redis production infrastructure.
- [AP-0028](../tickets/AP-0028-provider-isolation.md): Isolate blocking external provider clients.

## Completion Tracking

AP-0020 is complete. Its deployment increment establishes replica-safe application services, non-root image execution, bounded runtime resources, graceful shutdown, and local-only port publishing for development. `DeploymentComposeIT` builds and starts the canonical `springboot-postgresql-app` Compose service with the `postgres` profile in an isolated project and verifies the generated and running filesystem and resource constraints.

AP-0022 is in progress. Its application validation increment fails closed for missing or unsafe production dependency configuration, enforces the PostgreSQL-only runtime boundary, and verifies protected test-time Compose configuration. F07 is not complete: TLS entrypoint work (AP-0021), PostgreSQL/Redis hardening and recovery (AP-0026), external-provider isolation (AP-0028), OpenBao provisioning and rotation, and the remaining production operational controls are still required.

For the current readiness plan, complete AP-0022's operational secret-delivery prerequisites before AP-0026 and AP-0028. AP-0028 precedes AP-0021 because the proxy rollout and health-gated routing depend on known provider timeout and failure behavior. AP-0026 and AP-0028 can then proceed independently; AP-0021 follows AP-0028.
