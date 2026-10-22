# F07 Deployment and Secrets

Deploy stateless application instances behind TLS with isolated configuration and protected secrets.

**Status:** In Progress

- [AP-0020](../tickets/AP-0020-stateless-deployment.md): Deploy bounded non-root application replicas.
- [AP-0021](../tickets/AP-0021-tls-entrypoint.md): Configure Caddy, trusted proxies, and SSE timeouts.
- [AP-0022](../tickets/AP-0022-openbao-secrets.md): Establish OpenBao-backed secret delivery and rotation.
- [AP-0026](../tickets/AP-0026-data-infrastructure.md): Harden PostgreSQL and Redis production infrastructure.
- [AP-0028](../tickets/AP-0028-provider-isolation.md): Isolate blocking external provider clients.

## Completion Tracking

AP-0020 is complete. Its deployment increment establishes replica-safe application services, non-root image execution, bounded runtime resources, graceful shutdown, and local-only port publishing for development. The actual application image is built and started in the deployment container test under the declared filesystem and resource constraints. TLS, secret delivery, infrastructure hardening, and provider isolation remain separate follow-up tickets.
