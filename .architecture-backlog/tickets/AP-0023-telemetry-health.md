# AP-0023 Implement Unified Telemetry and Health

- **Status:** In Progress
- **Type:** Technical Enhancement
- **Feature:** [Observability and Recovery](../features/F08-observability-and-recovery.md)
- **Linked Tickets:** [AP-0020](AP-0020-stateless-deployment.md), [AP-0021](AP-0021-tls-entrypoint.md), [AP-0022](AP-0022-openbao-secrets.md), [AP-0026](AP-0026-data-infrastructure.md)
- **Source:** `deployment-architecture.md` Observability Solution

## Description
Provide correlated metrics, traces, structured logs, and safe health groups for requests, dependencies, and background work.

## Acceptance Criteria
1. Metrics cover HTTP, executors, PostgreSQL, Redis, wallets, notifications, SSE, and storage without high-cardinality labels.
2. Traces cover incoming requests, dependencies, and linked background jobs.
3. JSON logs share trace/correlation context and exclude sensitive values.
4. Public liveness/readiness expose safe information without JWT; detailed dependency health is protected.
5. Optional dependency failures follow documented degraded behavior.
6. Logs, metrics, and traces define environment-specific retention, access control, sampling, deletion, and storage budgets.
7. Micrometer exports Prometheus metrics, OpenTelemetry sends traces through a collector, and structured logs use the approved centralized log platform.

## Test Scenarios
1. **Given** a traced request, **when** signals emit, **then** correlation context links them.
2. **Given** a sensitive token, **when** an operation logs, **then** the value is absent.
3. **Given** an optional provider outage, **when** readiness runs, **then** unrelated traffic remains eligible.
4. **Given** an unauthorized operator, **when** observability data is requested, **then** access is denied.

## Definition of Done
The whole ticket is implemented, every acceptance criterion is satisfied, every scenario is automated and passing, dashboards and health documentation are complete, and all applicable suites pass.

## Implementation Tracking

- Application instrumentation now includes bounded wallet mutation, durable notification delivery, SSE connection/emission, storage operation/size, and provider metrics. Tags are selected from fixed low-cardinality sets; no user or object identifiers are accepted.
- Provider calls, notification recovery/delivery, and audit outbox publication/reconciliation/cleanup create Micrometer observations. Scheduled jobs get their own spans when there is no parent context. The PostgreSQL notification outbox does not persist trace context, so an originating request cannot yet be linked to later recovery work.
- Production Compose configures a separate management port, and the authentication filter skips JWT for Prometheus only when the local request port matches that configured management port. The observability Compose template keeps Prometheus, collector, Tempo, and Loki private, publishes Grafana on loopback with mandatory login, and supplies initial retention and resource limits.
- Public liveness/readiness groups omit component details. Readiness tracks application readiness, PostgreSQL R2DBC, and Redis. Audit PostgreSQL and optional providers do not remove business instances from rotation.
- Readiness tracks application readiness, PostgreSQL R2DBC, and Redis. Audit PostgreSQL and optional providers do not remove the business instance from rotation. Detailed health responses remain disabled on public actuator routes.
- Removed user identity and role values from successful authentication logs, and removed provider exception text from Firebase health details.
- Focused coverage verifies safe public probes, audit-outage degradation, and Firebase health redaction.
- Follow-up to review `41e2522`: request IDs are carried in Reactor context and bridged into MDC with Micrometer context propagation; the asynchronous scheduler-boundary log test verifies the structured logging context without clearing tracing MDC fields.
- Verification: `./mvnw -B test` passed (357 tests, 1 skipped); Docker-backed `./mvnw -B verify` passed (139 integration tests, 1 skipped).
- Review follow-up verification (2026-10-25): `./mvnw -B test` passed (358 tests, 1 skipped); Docker-backed `./mvnw -B verify` passed (139 integration tests, 1 skipped); JaCoCo passed.
- Remaining: deploy and verify the stack on staging; validate dashboard and network access control, sanitized-log handling, retention and deletion, durable storage quotas, export failure, and trace propagation. Exercise trace linkage across the durable notification outbox (origin context is not persisted). Configure/verify Alertmanager ownership. Compose files and application settings are not deployed-system evidence; AP-0023 remains In Progress.
