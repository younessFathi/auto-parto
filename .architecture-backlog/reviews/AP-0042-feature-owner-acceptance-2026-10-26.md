# AP-0042 Feature Owner Acceptance Review

- **Ticket:** [AP-0042 Complete Notification Delivery Reliability](../tickets/AP-0042-notification-delivery-reliability.md)
- **Review Status:** Approved
- **Implementation Status:** Unstaged
- **Reviewed At:** 2026-10-26

## Outcome

Accepted. PostgreSQL remains authoritative for notification intent and delivery state;
Redis is a bounded, expiring projection rebuilt from PostgreSQL. The review found that
active FCM controller logs exposed notification fields and provider exception details,
and failed delivery results could contain device tokens. Those outputs were redacted,
with focused regression tests added. Repeated delivery acknowledgement is covered by
PostgreSQL integration evidence. No alternate delivery architecture was introduced.

## Findings

### High

None.

### Medium

None after the review corrections described above.

### Low

1. Firebase uses a synchronous SDK call on `Schedulers.boundedElastic()` with a timeout.
   A dedicated bounded provider scheduler, saturation behavior, configurable capacity,
   and shutdown policy remain open under [AP-0028](../tickets/AP-0028-provider-isolation.md).
   This review does not mark provider isolation complete.

## Acceptance Criteria

| Criterion | Result | Evidence |
|---|---|---|
| PostgreSQL durably stores notification intent and its outbox event atomically. | Pass | `R2dbcNotificationRepository.enqueue` (`src/main/java/auto/parto/persistence/R2dbcNotificationRepository.java:42`); `enqueuesAndListsPendingNotification` (`src/test/java/auto/parto/persistence/R2dbcNotificationRepositoryIT.java:49`). |
| Claims are bounded, concurrent-safe, and represented by PostgreSQL owner and lease expiry. | Pass | `claimPendingForRecipient` uses `FOR UPDATE SKIP LOCKED` and a 1–100 limit (`R2dbcNotificationRepository.java:300`); `concurrentClaimsHaveOneWinnerAndLeaseExpiryAllowsRecovery` (`R2dbcNotificationRepositoryIT.java:294`). |
| Attempt rows, attempt count, retry time, controlled failure code, terminal exhaustion, and acknowledgement are durable. | Pass | `recordAttempt`, `acknowledgeDelivery`, and `scheduleRetry` (`R2dbcNotificationRepository.java:266`, `:342`, `:360`); `retryStateIsDurableAndReachesTerminalExhaustion` (`R2dbcNotificationRepositoryIT.java:316`). |
| Acknowledgement is guarded and duplicate acknowledgement is idempotently rejected. | Pass | Status and live lease owner are required by the update predicate (`R2dbcNotificationRepository.java:342`); `directDeliveryClaimAndAcknowledgementCompletesOneDurableRecord` verifies first `true`, then duplicate `false` (`R2dbcNotificationRepositoryIT.java:205`). |
| Redis notification projections are bounded, expiring, and rebuildable; PostgreSQL remains authoritative and there is no JVM-local durability fallback. | Pass | PostgreSQL-first store and best-effort Redis projection (`HybridNotificationStore.java:52`); projection rebuild from pending PostgreSQL rows (`HybridNotificationStore.java:179`); per-user cap/TTL and scan cap (`RedisNotificationStore.java:44`, `:77`, `:86`, `:257`); Redis replacement IT and PostgreSQL/Redis failure unit tests. Delivery/recovery state is persisted through repository methods; no local queue is used. |
| Async persistence chains are returned or subscribed by an owned lifecycle; recovery and delivery are bounded. | Pass | Startup and scheduled recovery subscriptions (`ResilientPendingNotificationService.java:51`, `:60`); bounded recipient pages and claims of 100 (`:66`, `:97`). |
| Logs and responses omit notification payloads, device tokens, and provider exception content. | Pass after correction | FCM controller logs generic delivery classifications; provider failures map to `PROVIDER_UNAVAILABLE` (`FcmNotificationController.java:81`, `:98`). Failed device token values are not returned (`ResilientFcmService.java:78`); token projection parse/serialization errors omit exception content (`RedisNotificationStore.java:280`, `:289`). `sendToUser_returnsSanitizedFailureWhenProviderThrows` (`FcmNotificationControllerTest.java:106`) and `sendToUserDoesNotReturnFailedDeviceTokens` (`ResilientFcmServiceTest.java:72`). |
| Provider isolation has bounded scheduler capacity, saturation behavior, and shutdown policy. | Partial; separately tracked | Synchronous Firebase call uses `Schedulers.boundedElastic()` and an FCM timeout (`ResilientFcmService.java:149`, `:151`). Dedicated isolation remains open in [AP-0028](../tickets/AP-0028-provider-isolation.md). |

## Test Scenarios

| Scenario | Result | Evidence |
|---|---|---|
| Duplicate delivery and duplicate acknowledgement | Pass with at-least-once semantics | Repository rejects a second acknowledgement; SSE event IDs are persisted notification UUIDs. Delivery may repeat if a connection drops after write but before acknowledgement; clients deduplicate by UUID. `R2dbcNotificationRepositoryIT.directDeliveryClaimAndAcknowledgementCompletesOneDurableRecord`; `SSENotificationServiceTest`. |
| Redis loss or unavailable projection | Pass | `HybridNotificationStoreTest.keepsDurableSuccessWhenRedisProjectionFails`; PostgreSQL-backed `rebuildProjection`; `RedisNotificationStoreIT.replacingPendingProjectionIsIdempotentAndBounded`. |
| Application restart recovery | Pass for the startup recovery path | `recoverOnStartupSubscribesAndProcessesPendingNotifications`; `NotificationSchedulingTest.scheduledRecoveryInvokesDueWork`; recovery rebuilds the projection before claiming (`ResilientPendingNotificationService.java:91`). |
| Concurrent claims and lease expiry | Pass | `R2dbcNotificationRepositoryIT.concurrentClaimsHaveOneWinnerAndLeaseExpiryAllowsRecovery`. |
| Retry timing and exhaustion | Pass | `R2dbcNotificationRepositoryIT.failedDirectDeliveryClearsLeaseAndKeepsTheSameRowRetryable` and `retryStateIsDurableAndReachesTerminalExhaustion`; 30 second delay and three attempts (`ResilientPendingNotificationService.java:23-25`). |
| Cancellation, reconnect, sink replacement, and safe cleanup | Pass | `NotificationControllerLifecycleTest` covers cancellation, stream completion, replacement, and cleanup; `SSENotificationServiceTest.registerConnectionCompletesOldSinkOnReconnect`. |
| Offline recipient preservation | Pass | `ResilientPendingNotificationServiceTest.scheduledRecoverySkipsOfflineRecipientsWithoutClaimingOrExhausting`; repository retains pending rows until eligible delivery. |
| Safe provider failure response and token omission | Pass after correction | `FcmNotificationControllerTest.sendToUser_returnsSanitizedFailureWhenProviderThrows`; `ResilientFcmServiceTest.sendToUserDoesNotReturnFailedDeviceTokens`. |

## Verification

- `git diff --check`: passed after all tracked changes; rerun against the staged change set before commit.
- Focused unit/controller/security set: passed, 34 tests, 0 failures/errors.
- `./mvnw -B verify`: passed, 380 unit/controller tests (1 skipped), 143 integration tests (1 skipped), Compose smoke test passed, coverage checks passed.
- `./mvnw -B -Dtest=R2dbcNotificationRepositoryIT test`: passed, 17 tests, 0 failures/errors after duplicate acknowledgement assertion.

## Required Before Approval

None for AP-0042. Keep provider scheduler isolation linked and open under AP-0028.

## Residual Risks

- Delivery is at-least-once; client deduplication by persisted event UUID is required.
- AP-0028 remains open for dedicated bounded Firebase provider isolation and saturation/shutdown behavior.
