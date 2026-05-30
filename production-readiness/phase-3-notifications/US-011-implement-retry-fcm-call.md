# US-011: Implement Actual FCM Call in Push Notification Retry Queue

**As a** developer  
**I want** the `PushNotificationQueue.attemptRedelivery()` method to make a real Firebase Cloud Messaging call instead of returning `Mono.just(true)`  
**so that** failed push notifications are actually retried and delivered instead of being silently dropped.

## Acceptance Criteria

1. `attemptRedelivery()` invokes `ResilientFcmService.sendToToken()` or equivalent real FCM call
2. Success: notification is removed from the retry queue
3. Failure: retry count is incremented; if below max retries, rescheduled with exponential backoff; if exhausted, moved to dead-letter queue
4. The TODO comment on line 122 is removed
5. Integration test verifies that a notification in the retry queue is eventually delivered (or dead-lettered)

## Technical Notes

- File: `src/main/java/auto/parto/service/notification/PushNotificationQueue.java` line 122
- Current code: `Mono.just(true)` — stub
- `ResilientFcmService` already has `sendToToken()` with retry and circuit breaker — wire it here
- Remove the `removeFromQueue` call from here since the FCM service handles it

## Risk

🔴 **CRITICAL** — Retry queue infrastructure is fully built (Redis sorted sets, scheduling, backoff, dead letters) but does nothing. Every retry attempt "succeeds" immediately.

## Dependencies

None.
