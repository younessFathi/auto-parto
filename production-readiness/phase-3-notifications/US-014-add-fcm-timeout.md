# US-014: Add Timeout to Firebase Cloud Messaging Calls

**As a** platform engineer  
**I want** `firebaseMessaging.send()` calls to have a configurable timeout  
**so that** a hanging FCM request does not block the scheduler thread indefinitely and potentially exhaust the thread pool.

## Acceptance Criteria

1. The `Mono.fromCallable(() -> firebaseMessaging.send(message))` in `ResilientFcmService` has a `.timeout()` operator applied
2. The timeout duration is configurable via `application.yml` (e.g., `firebase.messaging.timeout: 10s`)
3. When a timeout occurs:
   - The error is logged with the token (partially masked)
   - The error is treated as a retryable failure
4. Unit test verifies that a simulated slow FCM call triggers the timeout and is handled gracefully

## Technical Notes

- File: `src/main/java/auto/parto/service/notification/ResilientFcmService.java` line 136
- Current code: `Mono.fromCallable(() -> firebaseMessaging.send(message))` — no timeout
- Fix: `.timeout(Duration.ofSeconds(10)).onErrorResume(TimeoutException.class, ...)`
- Consider using the Reactor `Scheduler` for `fromCallable` to avoid blocking

## Risk

🟠 **HIGH** — Thread pool exhaustion under FCM latency spikes, cascading to all notification delivery.

## Dependencies

None.
