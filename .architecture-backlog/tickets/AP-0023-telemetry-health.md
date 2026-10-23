# AP-0023 Implement Unified Telemetry and Health

- **Status:** Proposed
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
