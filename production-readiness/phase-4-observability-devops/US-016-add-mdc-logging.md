# US-016: Inject Correlation ID into MDC for Structured Logging

**As** a developer  
**I want** the `X-Request-Id` correlation ID to be automatically injected into the MDC context for every request  
**so that** log aggregation tools (ELK, Loki, Datadog) can correlate all log entries belonging to a single request.

## Acceptance Criteria

1. `CorrelationIdWebFilter` sets the request ID into `MDC.put("requestId", requestId)` before the chain filter
2. `MDC.clear()` is called in a `finally` block or `.doFinally()` to prevent MDC leaks across requests
3. The logging pattern in `application.yml` includes `%X{requestId}` if using logback config
4. All `@Slf4j` log statements now include the correlation ID automatically
5. Integration test verifies that a log entry produced during a request contains the correlation ID

## Technical Notes

- File: `src/main/java/auto/parto/config/CorrelationIdWebFilter.java`
- The filter already generates and propagates `X-Request-Id` but does not set it in MDC
- Important: The reactive (WebFlux) context is different from imperative MDC — `MDC.put()` must happen on the subscription thread, and `.contextWrite()` may be more idiomatic for reactive stacks
- Alternative: Use `Hooks.enableAutomaticContextPropagation()` (Spring 6.1+) for reactive MDC support
- The generic exception handler should also include the request ID from the exchange attributes in its log output

## Risk

🟡 **MEDIUM** — Without correlation IDs in logs, debugging production issues across distributed requests is extremely difficult.

## Dependencies

None.
