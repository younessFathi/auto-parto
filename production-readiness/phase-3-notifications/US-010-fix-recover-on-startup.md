# US-010: Fix Pending Notification Recovery on Application Startup

**As a** user  
**I want** pending notifications that were stored during a server outage to be delivered when I reconnect  
**so that** I do not miss important updates when the application restarts.

## Acceptance Criteria

1. `ResilientPendingNotificationService.recoverOnStartup()` actually executes its reactive pipeline
2. On `ApplicationReadyEvent`, the method reads all pending notifications from Redis and processes them
3. The `@EventListener` method either:
   - Calls `.subscribe()` on the reactive chain, or
   - Uses `ApplicationListener<ApplicationReadyEvent>` with proper blocking/flatMap
4. Logs indicate the recovery process started, completed, and how many notifications were recovered
5. Integration test verifies that notifications stored in Redis before restart are delivered after restart

## Technical Notes

- File: `src/main/java/auto/parto/service/notification/ResilientPendingNotificationService.java` lines 77-103
- Current code returns `Mono<Object>` from `@EventListener` — Spring ignores the return value and the pipeline never executes
- The `@EventListener` signature should return `void` and internally call `.subscribe()` on the reactive chain, or use a different approach like `ApplicationListener<ApplicationReadyEvent>` with `onApplicationEvent()`

## Risk

🔴 **CRITICAL** — Notification recovery is completely dead code. Any notifications stored during an outage are permanently lost on restart.

## Dependencies

None.
