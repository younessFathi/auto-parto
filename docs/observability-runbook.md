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

## Production setup still required

Before relying on these signals for production response, deploy the Prometheus server, OpenTelemetry collector, and centralized log backend; restrict access; set retention and storage budgets; define sampling and deletion policy; configure Alertmanager; assign each alert an owner and recovery objective; and test a full alert-to-runbook exercise. AP-0023 and AP-0024 track these remaining controls.
