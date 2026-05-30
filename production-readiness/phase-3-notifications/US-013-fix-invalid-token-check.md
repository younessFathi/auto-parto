# US-013: Fix FCM Invalid Token Detection to Handle UNREGISTERED

**As a** developer  
**I want** `ResilientFcmService.isInvalidTokenError()` to check for `UNREGISTERED` error code in addition to `INVALID_ARGUMENT`  
**so that** device tokens that are no longer valid are cleaned up from Redis instead of being retried indefinitely.

## Acceptance Criteria

1. `isInvalidTokenError()` returns `true` for `MessagingErrorCode.UNREGISTERED` and `MessagingErrorCode.INVALID_ARGUMENT`
2. When an `UNREGISTERED` error is received from FCM:
   - The token is marked as invalid in Redis
   - The notification delivery is counted as a failure (but not retried since the token is invalid)
3. Integration test verifies that tokens returning `UNREGISTERED` are removed from the active token list

## Technical Notes

- File: `src/main/java/auto/parto/service/notification/ResilientFcmService.java` line 178
- Current code only checks `MessagingErrorCode.INVALID_ARGUMENT`
- `UNREGISTERED` is the most common error for expired or uninstalled app tokens
- Fix: `errorCode == MessagingErrorCode.UNREGISTERED || errorCode == MessagingErrorCode.INVALID_ARGUMENT`

## Risk

🟠 **HIGH** — Invalid tokens persist in Redis, causing repeated failed FCM sends, resource waste, and potential rate limiting by Firebase.

## Dependencies

None.
