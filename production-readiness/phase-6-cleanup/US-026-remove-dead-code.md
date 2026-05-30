# US-026: Remove Dead and Unused Code

**As a** developer  
**I want** dead code paths, unused beans, and partially migrated implementations to be removed  
**so that** the codebase is maintainable and developers are not misled by non-functional code.

## Acceptance Criteria

The following dead/unused code is removed or cleaned up:

1. **ClerkJwtAuthenticationFilter.java** — Entire file (placeholder public key, never wired in SecurityConfig)
2. **DeviceTokenService.userTokens** — In-memory map is never populated; `markTokenInvalid()` using this map does nothing. Remove the map and any logic that only operates on it.
3. **PendingNotificationService.java** — Old implementation; its in-memory queue is never populated (all writes go to Redis via `ResilientPendingNotificationService`). Either remove or document as deprecated.
4. **RetryConfig.fcmRetryTemplate** — Bean is injected into `ResilientFcmService` but never used (Reactor's `Retry.backoff` is used instead)
5. **SecurityHelper.validateAuthentication()** — Method is defined but never called from any controller/service
6. **JsonUtil dead imports** — Remove unused `Piece`, `PieceDto`, `PieceOfferDto` imports
7. **OtpDetails.UUID id** — Field on an embedded document that is never used as a document identity

## Technical Notes

- Check each removal: search for references across the entire codebase
- For `PendingNotificationService.java`, verify that `ResilientPendingNotificationService` is the active replacement
- For `DeviceTokenService.userTokens`, verify that `RedisNotificationStore.markTokenInvalid()` is the active replacement

## Risk

🟡 **MEDIUM** — Dead code increases maintenance burden and misleads developers about actual behavior. Some dead code paths (like the in-memory map in DeviceTokenService) create the illusion of functionality that doesn't exist.

## Dependencies

None.
