# AP-0023 Implement Unified Telemetry and Health

- **Status:** In Progress
- **Type:** Technical Enhancement
- **Feature:** [Observability and Recovery](../features/F08-observability-and-recovery.md)
- **Linked Tickets:** [AP-0020](AP-0020-stateless-deployment.md)
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

- Started the application telemetry baseline: Prometheus remains exposed, Micrometer Tracing is configured for OTLP with sampling and an explicit export opt-in, console logs use Spring Boot's JSON format, and public liveness/readiness groups omit component details.
- Readiness tracks application readiness, PostgreSQL R2DBC, and Redis. Audit PostgreSQL and optional providers do not remove the business instance from rotation. Detailed health responses remain disabled on public actuator routes.
- Removed user identity and role values from successful authentication logs, and removed provider exception text from Firebase health details.
- Focused coverage verifies safe public probes, audit-outage degradation, and Firebase health redaction.
- Verification: `./mvnw -B test` passed (357 tests, 1 skipped); Docker-backed `./mvnw -B verify` passed (139 integration tests, 1 skipped).
- Remaining: confirm the central log backend, collector, Prometheus/Grafana deployment, retention/access/sampling budgets, protected operator diagnostics, domain metrics for wallets/notifications/SSE/storage, trace continuity through provider calls and background work, and broader log review/redaction. AP-0023 remains In Progress until these are deployed and verified.
