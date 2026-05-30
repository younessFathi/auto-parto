# US-012: Fix SSE Connection Sink Leak and Add Resource Bounds

**As a** platform engineer  
**I want** SSE user connections to be properly cleaned up on reconnect and resource-bounded  
**so that** orphaned reactive sinks do not leak memory over time and the server cannot be overloaded by excessive connections.

## Acceptance Criteria

1. `SSENotificationService.registerConnection()` uses `computeIfAbsent` with proper cleanup:
   - Old sink is completed/disposed before replacement
   - Concurrent calls for the same userId do not orphan sinks
2. Sink buffer is bounded (e.g., `onBackpressureBuffer(1000)` instead of unbounded)
3. Maximum concurrent connections is enforced (configurable via `application.yml`)
4. `unregisterConnection()` verifies the sink being removed is the correct one (not a replacement)
5. `sendViaSSE` timeout handling is fixed — current logic has a race between the subscription and the emission
6. Integration test verifies that 1000 rapid connect/disconnect cycles do not increase memory significantly

## Technical Notes

- File: `src/main/java/auto/parto/service/notification/SSENotificationService.java`
- Current pattern: `sinks.put(userId, sink)` — overwrites without cleanup
- Fix: `userConnections.compute(userId, (k, existing) -> { if (existing != null) existing.tryEmitComplete(); return newSink; })`
- Unbound sink: `Sinks.many().unicast().onBackpressureBuffer()` — add max buffer size
- `sendViaSSE` has a logic error: subscribes to `sink.asFlux()` (consumes the item) then tries to `emitNext` the same item

## Risk

🟠 **HIGH** — Orphaned sinks with unbounded buffers cause OOM under sustained reconnect load (common with SSE clients).

## Dependencies

None.
