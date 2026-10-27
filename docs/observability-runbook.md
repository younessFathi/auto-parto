# Observability Runbook

## Application signals

- Public probes: `GET /actuator/health/liveness` and `GET /actuator/health/readiness`. They return status only and require no application JWT.
- Prometheus: `GET /actuator/prometheus`. It is not public; scrape it only through a private, authenticated management path.
- Traces: set `OTEL_TRACES_EXPORT_ENABLED=true`, `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT`, and `OTEL_TRACES_SAMPLING_PROBABILITY` through protected deployment configuration. The default sampling rate is 10%; exporting is disabled unless explicitly enabled.
- Logs: console output uses Logstash-compatible JSON. Request IDs are added through MDC. Trace and span identifiers appear when tracing instrumentation has established them.

Request IDs are stored in Reactor context and restored into MDC through Micrometer context propagation, including across scheduler changes. The filter scopes only the request ID key so it does not clear trace/span fields maintained by tracing instrumentation.

## Readiness response

Readiness combines application readiness with the PostgreSQL R2DBC and Redis indicators. Audit PostgreSQL and optional providers do not remove the entire application from traffic rotation. Probe responses omit component names and details, so they are suitable for routing but not dependency diagnosis.

If readiness is `DOWN`, first confirm that the instance is still live. Then use the private operations channel to inspect PostgreSQL and Redis health, connection saturation, and recent application logs. Do not add provider exception text, credentials, user IDs, tokens, or notification payloads to logs or incident notes. The repository does not yet provide a private actuator diagnostics endpoint; until that exists, use authenticated infrastructure dashboards and service logs for diagnosis.

## Deployment template and production verification still required

`deploy/observability/compose.yml` provides a private-network template for Prometheus, the OpenTelemetry collector, Tempo, Loki, and Grafana. Run it only after creating the `auto-parto-app-backend` and `auto-parto-observability` networks and setting a strong operator password through protected deployment configuration. Grafana binds to host loopback; connect with an SSH tunnel. Do not publish Prometheus, Tempo, Loki, or collector ports. The application management listener must remain private and is the only port Prometheus scrapes.

Initial template retention is Prometheus 15 days and at most 5 GB, Tempo 72 hours, and Loki 7 days. Prometheus has an explicit TSDB size cap. Docker named volumes do not enforce storage quotas for the other services: provision filesystem quotas and alerts on the host before staging, and test behavior at quota. Loki retention deletion and operator-initiated deletion must be exercised and recorded. The collector samples traces according to `OTEL_TRACES_SAMPLING_PROBABILITY` (5% in the production Compose default) and applies common credential redaction rules to JSON logs. Validate these rules using sanitized fixtures; they are defense in depth, not a substitute for safe application logs.

Before relying on these signals for production response, deploy and verify this stack on staging; test unauthorized dashboard access, private port reachability, redaction, retention expiry, deletion, disk quotas, and export failure. Configure Alertmanager, assign each alert an owner and recovery objective, and test a full alert-to-runbook exercise. AP-0023 and AP-0024 track these controls. AP-0024 recovery drills must wait until AP-0021, AP-0022, AP-0023, and AP-0026 have demonstrable infrastructure evidence.
