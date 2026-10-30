# F08 Observability and Recovery

Make application and infrastructure behavior measurable, diagnosable, and recoverable.

**Status:** In Progress

- [AP-0023](../tickets/AP-0023-telemetry-health.md): Add unified metrics, traces, logs, and health groups.
- [AP-0024](../tickets/AP-0024-alerts-recovery.md): Implement alerts, backups, restoration, and recovery drills.

## Current Readiness Sequence

1. AP-0023 has application metrics, provider/background observations, safe probes, and a protected deployment template. It remains In Progress until a staging deployment verifies access controls, redaction, retention/deletion, storage quotas, and trace continuity.
2. AP-0024 is Blocked until AP-0021, AP-0022, AP-0023, and AP-0026 have deployed-system evidence. AP-0013 and AP-0019 are Done, but their completion does not satisfy these infrastructure dependencies.

AP-0033 is an independent CI migration track and remains In Progress. Keep GitLab CI active until GitHub Actions has passed equivalent compile, unit, Docker/Testcontainers verification, and package runs, and the protected migration gate has been verified in a separate deployment repository/orchestrator. Application-repository workflows must not have access to private database runners or credentials.
