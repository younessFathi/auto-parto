# F08 Observability and Recovery

Make application and infrastructure behavior measurable, diagnosable, and recoverable.

**Status:** In Progress

- [AP-0023](../tickets/AP-0023-telemetry-health.md): Add unified metrics, traces, logs, and health groups.
- [AP-0024](../tickets/AP-0024-alerts-recovery.md): Implement alerts, backups, restoration, and recovery drills.

## Current Readiness Sequence

1. AP-0023 is the first implementation slice because its only linked prerequisite, AP-0020, is Done. Application-side metrics, traces, structured logs, and safe probe groups are being established before alert thresholds and recovery evidence are finalized.
2. AP-0024 follows AP-0021, AP-0022, and AP-0023, plus the AP-0026 infrastructure controls needed to exercise backups and restoration. AP-0013 and AP-0019 are already Done.

AP-0033 is an independent CI migration track and remains In Progress. Keep GitLab CI active until the GitHub Actions workflow has passed the equivalent compile, unit, Docker/Testcontainers verification, package, and protected/manual migration gates.
