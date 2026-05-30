# US-021: Externalize Thread Pool Configurations

**As** an operations engineer  
**I want** thread pool sizes in `ExecutorConfig` to be configurable via `application.yml`  
**so that** thread pool sizes can be tuned per environment without recompilation.

## Acceptance Criteria

1. `ExecutorConfig` reads pool sizes from `@Value` or `@ConfigurationProperties`:
   - `auto.parto.executor.retry.pool-size: 3`
   - `auto.parto.executor.notification-retry.pool-size: 2`
2. Defaults are defined in `application.yml` so the app works out-of-the-box
3. The Guava `ThreadFactoryBuilder` usage is replaced with a standard Java `ThreadFactory` to remove the Guava dependency, OR both executors consistently use the same approach
4. Consider adding a unified `ScheduledExecutorService` bean with configurable pool size instead of two separate beans
5. `RejectedExecutionHandler` is configured to log and discard (or use `CallerRunsPolicy`)

## Technical Notes

- File: `src/main/java/auto/parto/config/ExecutorConfig.java`
- Current state: hardcoded `Executors.newScheduledThreadPool(3)` and `Executors.newScheduledThreadPool(2)`
- The Guava dependency (`ThreadFactoryBuilder`) is used only in one of the two executors — inconsistent
- Having multiple `ScheduledExecutorService` beans is fragile (injection by parameter name) — add `@Qualifier` annotations to the injection points

## Risk

🟡 **MEDIUM** — Hardcoded thread pools cannot be tuned for production load. Bean ambiguity could cause runtime injection failures.

## Dependencies

None.
