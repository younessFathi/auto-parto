# F07 Deployment and Secrets

Deploy stateless application instances behind TLS with isolated configuration and protected secrets.

**Status:** In Progress

- [AP-0020](../tickets/AP-0020-stateless-deployment.md): Deploy bounded non-root application replicas.
- [AP-0021](../tickets/AP-0021-tls-entrypoint.md): Configure Caddy, trusted proxies, and SSE timeouts.
- [AP-0022](../tickets/AP-0022-openbao-secrets.md): Cancelled/superseded for production by ADR-0001. Its deployment prototype and tests have been removed; historical evidence does not count toward F07 completion.
- [AP-0044](../tickets/AP-0044-openbao-vps-go-live.md): Cancelled/superseded by ADR-0001. No OpenBao production service will deliver initial credentials.
- [AP-0045](../tickets/AP-0045-ansible-vault-vps-secrets.md): In Progress implementing the ADR-0001 decision: operator-controlled Ansible Vault deploy-time injection. Initial file-import and playbook changes are in the repository; staging, rotation/rejection, password recovery, and host-recovery evidence remain outstanding.
- [AP-0046](../tickets/AP-0046-provision-vps-topology-with-opentofu-ansible.md): Proposed Contabo OpenTofu/Ansible four-VPS topology. Secret-model choice is resolved; region/account/cost, private-network/provider validation, and external state-changing runner remain blockers.
- [AP-0026](../tickets/AP-0026-data-infrastructure.md): In Progress. Verified PostgreSQL TLS, CA/hostname-verified Redis TLS, effective bounded Redis/app/audit pools, role connection ceilings, and local Redis memory/network limits are implemented; private VPS networking, measured sizing, encrypted off-host backup/PITR, Redis HA, and recovery evidence remain open.
- [AP-0028](../tickets/AP-0028-provider-isolation.md): Isolate blocking external provider clients.

## Completion Tracking

AP-0020 is complete. Its deployment increment establishes replica-safe application services, non-root image execution, bounded runtime resources, graceful shutdown, and local-only port publishing for development. `DeploymentComposeIT` builds and starts the canonical `springboot-postgresql-app` Compose service with the `postgres` profile in an isolated project and verifies the generated and running filesystem and resource constraints.

AP-0022's OpenBao production scope is cancelled by ADR-0001; its deployment prototype and tests have been removed, and the ticket remains Cancelled rather than Done. AP-0045 has started implementing Ansible Vault file delivery and bounded host-side deployment, but staging and operational evidence remain outstanding. F07 remains In Progress for AP-0021, AP-0024, AP-0026, AP-0045 completion, and remaining production controls.

OpenBao is not part of the active deployment configuration. AP-0045 tracks the selected Ansible Vault implementation; AP-0022 and AP-0044 remain cancellation history only.

AP-0028 is Done: Firebase uses the Admin SDK async future API with bounded in-flight/batch concurrency, Resend's synchronous SDK runs on a finite bounded scheduler, and S3 retains the AWS async client with finite connection/pending-acquisition and operation timeouts. Provider calls report low-cardinality outcome metrics and redacted failure logs. Focused isolation tests and the S3Mock Testcontainers integration test pass, and full unit/verify results are recorded in the session trace. This implementation does not change PostgreSQL-authoritative notification delivery. F07 remains In Progress for AP-0021, AP-0045 implementation and operational evidence, AP-0024, AP-0026, and other remaining deployment controls.

For implementation readiness, AP-0026 and AP-0028 can proceed with isolated test credentials and providers; that does not permit go-live. AP-0028 precedes AP-0021 because the proxy rollout and health-gated routing depend on known provider timeout and failure behavior. AP-0026 and AP-0028 can proceed independently; AP-0021 follows AP-0028. ADR-0001 resolves the secret model; AP-0045 is In Progress and AP-0046 remains Proposed pending provider and runner prerequisites. F07 remains short of Done until selected-model implementation and operational evidence are complete.
