# US-028: Fix Redis Pending Notification Cleanup TTL Bug

**As a** developer  
**I want** the `RedisNotificationStore.cleanupExpiredNotifications()` scheduled task to correctly check TTL values  
**so that** pending notifications without an expiry are not accidentally deleted and the `KEYS` command is replaced with `SCAN` to avoid blocking Redis.

## Acceptance Criteria

1. The TTL check `.filter(ttl -> ttl != null && ttl.getSeconds() <= 0)` is fixed:
   - Keys with no expiry (TTL = -1) are NOT deleted
   - Keys that do not exist (TTL = -2) are skipped
   - Only keys with TTL > 0 and actually expired (TTL ≤ 0, but > -1) are deleted
2. The `KEYS notification:pending:*` command is replaced with `SCAN 0 MATCH notification:pending:* COUNT 100`
3. Integration test verifies that:
   - An expired key is cleaned up
   - A key with no expiry is NOT cleaned up
   - The scan-based approach correctly iterates over all matching keys

## Technical Notes

- File: `src/main/java/auto/parto/service/notification/RedisNotificationStore.java`
- Lines: cleanup method around line 217-221
- Current TTL check: `ttl.getSeconds() <= 0` — matches -1 (no expiry) and treats it as expired
- Fix: `filter(ttl -> ttl != null && ttl.getSeconds() > -1 && ttl.getSeconds() <= 0)` is logically impossible — the correct check depends on how Redis represents TTL. Spring Data Redis `getExpire()` returns `Duration.ofSeconds(-1)` for no expiry and `Duration.ofSeconds(-2)` for missing key.
- Use `SCAN` via `RedisConnection.scan()` or Spring Data Redis's `Cursor`-based scan

## Risk

🟠 **HIGH** — Data loss bug: pending notifications without a TTL (edge case) are incorrectly deleted by the daily cleanup job.

## Dependencies

None.
