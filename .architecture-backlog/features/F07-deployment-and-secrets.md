# F07 Deployment and Secrets

Deploy stateless application instances behind TLS with isolated configuration and protected secrets.

**Status:** In Progress

- [AP-0020](../tickets/AP-0020-stateless-deployment.md): Deploy bounded non-root application replicas.
- [AP-0021](../tickets/AP-0021-tls-entrypoint.md): Configure Caddy, trusted proxies, and SSE timeouts.
- [AP-0022](../tickets/AP-0022-openbao-secrets.md): Establish OpenBao-backed secret delivery and rotation. Repository/image configuration and local Docker evidence are complete, but AP-0022 remains In Progress until the deployed service, workload identities, live rotation, and recovery drills meet its full acceptance criteria. [AP-0044](../tickets/AP-0044-openbao-vps-go-live.md) tracks approved VPS evidence and does not close AP-0022.
- [AP-0044](../tickets/AP-0044-openbao-vps-go-live.md): Provision and verify the private OpenBao service and workload integration when production go-live is approved. A non-production dev-mode container is disposable and is not production evidence.
- [AP-0026](../tickets/AP-0026-data-infrastructure.md): Harden PostgreSQL and Redis production infrastructure.
- [AP-0028](../tickets/AP-0028-provider-isolation.md): Isolate blocking external provider clients.

## Completion Tracking

AP-0020 is complete. Its deployment increment establishes replica-safe application services, non-root image execution, bounded runtime resources, graceful shutdown, and local-only port publishing for development. `DeploymentComposeIT` builds and starts the canonical `springboot-postgresql-app` Compose service with the `postgres` profile in an isolated project and verifies the generated and running filesystem and resource constraints.

AP-0022 remains In Progress. Its repository and image-configuration increment is implemented: the production profile imports required credentials from an OpenBao Agent rendered file; the app container does not receive the Agent token; the systemd app unit gates startup on a complete, correctly permissioned bundle; and the repository includes a digest-pinned normal-mode image Compose template, TLS/Raft configuration, policies, Agents, staging scripts, and RS256/JWKS overlap/revocation behavior. `OpenBaoImageDeploymentIT` verifies local TLS startup, policy isolation, workload identity rotation/revocation, and restart recovery. No actual VPS service, production workload identity, credential rotation/old-value rejection, quorum unseal, or snapshot restore has been exercised. AP-0044 tracks approved deployment evidence but does not change AP-0022 status. F07 is not complete: TLS entrypoint work (AP-0021), PostgreSQL/Redis hardening and recovery (AP-0026), external-provider isolation (AP-0028), AP-0022's live service and recovery criteria, and remaining production controls are still required.

`docker-compose.openbao-dev.yml` is a separate local-only sandbox using the official OpenBao image in memory-only dev mode, published on loopback and with a throwaway dev token. It is not wired into the application production profile, is not suitable for real credentials, and does not satisfy AP-0022's live identity, persistence, or recovery requirements. Production VPS/service work is tracked separately by proposed AP-0044.

AP-0028 is Done: Firebase uses the Admin SDK async future API with bounded in-flight/batch concurrency, Resend's synchronous SDK runs on a finite bounded scheduler, and S3 retains the AWS async client with finite connection/pending-acquisition and operation timeouts. Provider calls report low-cardinality outcome metrics and redacted failure logs. Focused isolation tests and the S3Mock Testcontainers integration test pass, and full unit/verify results are recorded in the session trace. This implementation does not change PostgreSQL-authoritative notification delivery. F07 remains In Progress for AP-0021, AP-0022 live operational evidence, AP-0024, AP-0026, and other remaining deployment controls.

For implementation readiness, AP-0026 and AP-0028 can proceed with isolated test credentials and providers; that does not satisfy AP-0022's production acceptance criteria or permit go-live. AP-0028 precedes AP-0021 because the proxy rollout and health-gated routing depend on known provider timeout and failure behavior. AP-0026 and AP-0028 can proceed independently; AP-0021 follows AP-0028. AP-0044 tracks actual production OpenBao provisioning and operations evidence while AP-0022 remains In Progress until its full criteria are met.
