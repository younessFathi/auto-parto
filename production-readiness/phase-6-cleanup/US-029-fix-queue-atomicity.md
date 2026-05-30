# US-029: Fix Atomicity and Memory Issues in Push Notification Retry Queue

**As a** developer  
**I want** `PushNotificationQueue.removeFromQueue()` to use an atomic Lua script and avoid loading all retry items into memory  
**so that** the retry queue is consistent under concurrent access and does not cause OOM for users with many queued notifications.

## Acceptance Criteria

1. `removeFromQueue()` uses a Redis Lua script that atomically:
   - Finds the matching member by score range or member value
   - Removes it from the sorted set
   - Returns whether a removal occurred
2. The current Java-side `ZRANGE` → iterate → `ZREM` pattern is removed
3. Dead-letter list (`push:dead:{userId}`) has a configurable maximum size (e.g., 1000 entries) enforced via `LTRIM` after each push
4. Integration test verifies that concurrent `removeFromQueue` calls for the same user do not lose entries

## Technical Notes

- File: `src/main/java/auto/parto/service/notification/PushNotificationQueue.java`
- Current `removeFromQueue()` at line 199:
  1. `ZRANGE key 0 -1` — loads ALL members into memory
  2. Java iteration to find matching notification ID
  3. `ZREM key member` — non-atomic removal
- Lua script approach:
  ```lua
  local members = redis.call('ZRANGE', KEYS[1], 0, -1)
  for i, member in ipairs(members) do
      local decoded = cjson.decode(member)
      if decoded.notificationId == ARGV[1] then
          redis.call('ZREM', KEYS[1], member)
          return 1
      end
  end
  return 0
  ```
- Dead letter limit: after `RPUSH`, call `LTRIM key -1000 -1` to keep only the latest 1000

## Risk

🟡 **MEDIUM** — Non-atomic removal can lose notifications under concurrent access. Loading all members into memory is a DoS vector.

## Dependencies

US-011 (retry queue must be functional first)
