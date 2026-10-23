# AP-0020 Deploy Stateless Application Replicas

- **Status:** Done
- **Type:** Technical Enhancement
- **Feature:** [Deployment and Secrets](../features/F07-deployment-and-secrets.md)
- **Linked Tickets:** [AP-0002](AP-0002-reactive-persistence.md), [AP-0016](AP-0016-redis-projections.md)
- **Source:** `deployment-architecture.md` Application Deployment

## Description
Run horizontally scalable application instances without instance-affine durable state.

## Acceptance Criteria
1. Containers run non-root with CPU, memory, connection, and executor limits.
2. Sessions, pending work, tokens, and business data do not depend on one instance.
3. Graceful shutdown stops new work and gives bounded in-flight work time to finish.
4. Logs go to standard output and images contain no secrets.

## Test Scenarios
1. **Given** one instance terminates, **when** traffic continues, **then** healthy replicas continue serving.
2. **Given** shutdown starts, **when** work is active, **then** bounded graceful completion occurs.
3. **Given** a running container, **when** inspected, **then** it is non-root and within limits.

## Definition of Done
The whole ticket is implemented, every acceptance criterion is satisfied, every scenario is automated and passing, deployment configuration is documented, and all applicable suites pass.

## Implementation Tracking

- AP-0020 is complete: the application image runs as the dedicated non-root `spring` user and contains no runtime credentials.
- Application services no longer use fixed container names or published instance ports. The base Compose deployment exposes port 8080 only on the private network, allowing replicas to scale behind a reverse proxy. `docker-compose.local.yml` provides loopback-only ports for local development.
- Compose services have bounded CPU, memory, process, and log resources, a read-only root filesystem with a temporary filesystem for `/tmp`, and a 20-second graceful stop window. The canonical `springboot-postgresql-app` service is activated with the `postgres` profile and is ordered after PostgreSQL migration and fixtures.
- Spring Boot uses graceful shutdown with a bounded 20-second phase timeout, bounded codec buffers, configurable R2DBC/Redis pools, and bounded retry executor sizes.
- `DockerComposeConfigurationTest`, `DeploymentContractTest`, and `DeploymentComposeIT` cover the deployment contract, including replica-safe service configuration, non-root execution, and bounded container resources. `DeploymentComposeIT` builds and starts the actual `springboot-postgresql-app` service with the `postgres` profile from `docker-compose.yml` in an isolated Compose project, verifies liveness, and inspects the generated and running container for the declared read-only root, writable `/tmp`, non-root user, and resource limits.
