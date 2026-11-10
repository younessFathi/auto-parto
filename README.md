# Auto-Parto

Java 21 / Spring Boot WebFlux backend for the car-parts marketplace. Local development runs the application, PostgreSQL and Redis with Docker Compose.

## Functional workflow diagrams

Explore [the functional journeys](docs/workflows.md) from the buyer and seller perspective:

- [Join or sign in](docs/workflows.md#join-or-sign-in) and [manage your profile](docs/workflows.md#manage-a-profile-or-deactivate-an-account).
- [Request a part](docs/workflows.md#request-a-car-part), [publish a listing](docs/workflows.md#publish-and-manage-a-listing) and [find matching parts](docs/workflows.md#find-parts-matching-a-request).
- [Make and decide on offers](docs/workflows.md#make-and-decide-on-an-offer) and [credit or debit a seller wallet](docs/workflows.md#credit-or-debit-a-seller-wallet).
- [Attach photos](docs/workflows.md#attach-photos-to-a-request-or-listing) and [view images](docs/workflows.md#view-an-uploaded-image).
- [Choose notification preferences](docs/workflows.md#choose-notification-preferences), [receive updates](docs/workflows.md#receive-account-notifications) and [enable device push](docs/workflows.md#enable-push-notifications-on-a-device).

For development, infrastructure and agent collaboration, see the separate [technical workflows](docs/technical-workflows.md). Local startup configuration and commands remain in the [PostgreSQL development guide](docs/postgresql-development-cutover.md).

## Start locally with Docker Compose

### 1. Prerequisites

- Docker Desktop (macOS/Windows) or Docker Engine (Linux), with Docker Compose v2. Start Docker before running the commands below.
- Git and a local checkout of this repository. Run commands from the repository root.
- Free local ports `8081` (application) and `6379` (Redis).
- OpenSSL to generate development credentials. Java and Maven are included in the Docker build image; a host Java 21 installation is needed only for Maven commands outside Docker.

Check Docker is available:

```bash
docker info
docker compose version
```

### 2. Configure the local environment

Create `.env` only if it does not already exist:

```bash
test -f .env || cp .env.example .env
```

Edit `.env` and set these values. The role names below are local defaults; passwords must be distinct generated values.

| Variable | Local value / purpose |
| --- | --- |
| `APP_POSTGRES_DB` | `auto_parto` |
| `APP_POSTGRES_BOOTSTRAP_USER` | `postgres`; database initialization account |
| `APP_POSTGRES_BOOTSTRAP_PASSWORD` | Generated bootstrap password |
| `APP_POSTGRES_RUNTIME_USER` | `auto_parto_runtime`; application/fixture account |
| `APP_POSTGRES_RUNTIME_PASSWORD` | Generated runtime password |
| `APP_POSTGRES_MIGRATION_USER` | `auto_parto_migrator`; Flyway schema account |
| `APP_POSTGRES_MIGRATION_PASSWORD` | Generated migration password |
| `REDIS_PASSWORD` | Generated Redis password |
| `JWT_SECRET` | Base64-encoded key containing at least 32 random bytes |

Generate a fresh value for each password, then a separate JWT key:

```bash
openssl rand -hex 32
openssl rand -base64 32
```

Keep `.env` local and Git-ignored. If reusing an existing database or Redis volume, use its established credentials. Changing `.env` does not rotate an existing PostgreSQL role password or rerun first-volume initialization; follow the [existing-volume procedure](docs/postgresql-development-cutover.md#local-postgresql-schema-migration) when roles or grants need repair. Preserve existing uploads and database volumes.

OTP email delivery is disabled by default (`resend.enable=false`). To enable it, supply a valid `RESEND_API_KEY` and explicitly add `RESEND_ENABLE: "true"` to the application service environment in a Compose override. Setting `RESEND_ENABLE` in `.env` alone does not pass it through the base Compose file. The example API-key placeholder supports startup and browsing only, not email delivery. Firebase components are disabled by this Compose profile. Production secrets and object-storage examples in `.env.example` are separate settings, not prerequisites for this local Compose workflow.

### 3. Validate and start

Use the same `auto-parto` Compose project name across restarts so the named data volumes remain the same. Run from the checkout that owns your local `uploads` directory; if moving checkouts, preserve that directory or explicitly bind its existing location.

The local override publishes the application and Redis on loopback only. PostgreSQL remains on the Docker network. The base Compose file alone does not expose the application to your workstation.

```bash
mkdir -p uploads

docker compose --project-name auto-parto -f docker-compose.yml -f docker-compose.local.yml --profile postgres config --quiet

docker compose --project-name auto-parto -f docker-compose.yml -f docker-compose.local.yml --profile postgres up -d --build --wait --wait-timeout 180 springboot-postgresql-app
```

Startup follows these dependencies:

1. Start PostgreSQL and Redis. On a fresh database volume, PostgreSQL provisions the restricted runtime and migration roles.
2. Once PostgreSQL is healthy, apply pending Flyway migrations and require success.
3. Apply idempotent local development fixtures after migration succeeds.
4. Once fixtures have completed successfully and Redis is healthy, start the `local` Spring application and wait for health, including storage reconciliation.

The first image build downloads dependencies and can take several minutes. `--wait-timeout` bounds service readiness after the build. Rerun the same `up` command to rebuild changed source; existing named database/Redis volumes and `./uploads` are reused. The application image includes Java 21.

### 4. Open Swagger and check health

- [Swagger UI](http://localhost:8081/swagger-ui/index.html)
- [OpenAPI JSON](http://localhost:8081/v3/api-docs)
- [Aggregate health used by Compose](http://localhost:8081/actuator/health)
- [Liveness probe](http://localhost:8081/actuator/health/liveness)
- [Readiness probe](http://localhost:8081/actuator/health/readiness)

```bash
curl --fail http://localhost:8081/actuator/health
curl --fail http://localhost:8081/actuator/health/liveness
curl --fail http://localhost:8081/actuator/health/readiness
```

Aggregate health must return `UP` for Compose to consider the application healthy; it includes the enabled storage-reconciliation gate. The narrower readiness probe checks `readinessState`, PostgreSQL (`r2dbc`) and Redis, while liveness checks `livenessState`. Both probes should also return `UP`, but readiness alone does not prove storage reconciliation succeeded. The existing Swagger implementation is currently accessible without an admin token; the proposed protected, domain-grouped version is future AP-0048 work.

### 5. View logs or stop

```bash
docker compose --project-name auto-parto -f docker-compose.yml -f docker-compose.local.yml --profile postgres ps

docker compose --project-name auto-parto -f docker-compose.yml -f docker-compose.local.yml --profile postgres logs --tail 100 -f springboot-postgresql-app

docker compose --project-name auto-parto -f docker-compose.yml -f docker-compose.local.yml --profile postgres stop
```

`stop` preserves the containers and data. Start them again with the `up` command above. Volume deletion is not a startup or migration-repair step.

### Troubleshooting

- **Required environment variable is missing:** complete the Compose variables listed above; legacy `DB_USER`, `DB_PASSWORD` and `URI` values do not replace the PostgreSQL role settings.
- **PostgreSQL authentication, grants or migration failure:** keep existing-volume credentials, inspect the failed migration service and use the [development migration guide](docs/postgresql-development-cutover.md). Do not continue application rollout after a failed migration.
- **Application stays unhealthy:** inspect application logs and aggregate `/actuator/health`, which Compose uses. Storage reconciliation keeps its aggregate-health contribution down for missing active objects or a timeout; the narrower readiness probe omits this gate. Do not disable the guard to make startup appear successful. Confirm the mounted uploads directory is writable by the container's `spring` user.
- **Missing `Duration` bean in `StorageReconciliationStartupGate`:** use a version containing the timeout-binding fix. The gate must bind `auto.parto.persistence.postgresql.cutover.storage-reconciliation.timeout` (default `30s`); adding an unrelated global `Duration` bean is not required.
- **Docker daemon does not respond:** check Docker Desktop/Engine before retrying Compose.
- **Port already allocated:** another local instance may already be using `8081` or `6379`; inspect `docker ps` before starting another checkout.

## Run tests on the host

With Java 21 installed and Docker running for integration tests:

```bash
./mvnw -B test
./mvnw -B verify
```

## PostgreSQL development migration

PostgreSQL is the application persistence boundary. To run the schema and fixtures locally, follow [`docs/postgresql-development-cutover.md`](docs/postgresql-development-cutover.md). The Compose profile provisions distinct runtime and migration roles, runs Flyway as a one-shot job, and provides idempotent development fixtures.

Authentication currently uses the OTP-to-JWT canonical model documented in [`docs/canonical-authentication.md`](docs/canonical-authentication.md). Incomplete Google and Clerk integrations remain disabled.

The canonical versioned HTTP API and legacy-route migration policy are documented in [`docs/api-boundaries.md`](docs/api-boundaries.md).

## Agent collaboration

Codex feature delivery uses Coder, Reviewer and DevOps with independent commit approvals and a GitHub PR handoff for human review. Read [the agent workflow](docs/agent-workflow.md). Open Codex in this repository to load `.codex/config.toml` and `.codex/agents/`; model settings inherit from the current chat. Local progress is recorded in ignored `handover.md` using [the versioned template](docs/agent-handover-template.md).

Coder can also suggest evidence-based product, technical and workflow improvements through [feature proposals](docs/feature-proposals/README.md). Reviewer and DevOps assess each proposal; implementation requires your explicit approval.
