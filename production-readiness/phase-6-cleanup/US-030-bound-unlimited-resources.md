# US-030: Bound All Unlimited Resources (Caches, Queues, Connections)

**As a** platform engineer  
**I want** all in-memory and Redis data structures that can grow without bound to have configurable maximum sizes  
**so that** the application cannot be driven into OOM or Redis memory exhaustion by a bug or attack.

## Acceptance Criteria

The following resources are bounded:

1. **SecretsManagerService.keyCache** — Replace the current `ConcurrentHashMap` with a `Caffeine` cache (max 100 entries, 1-hour expiry)
2. **SSENotificationService.userConnections** — Maximum concurrent connections enforced (configurable, default 10000)
3. **SSENotificationService sink buffers** — Each sink has a maximum buffer size (configurable, default 1000)
4. **BasicCircuitBreaker.failureCounts** — Set a max age (e.g., entries older than 5 minutes are evicted) to prevent map growth from abandoned users
5. **PushNotificationQueue dead-letter lists** — Maximum 1000 entries per user (see US-029)
6. **Rate limiting Redis keys** — Verify that all rate limit keys have TTLs (currently they do on first write; confirm EXPIRE is always set)

## Acceptance Criteria (Monitoring)

7. Each bounded resource logs a WARN when approaching its limit (e.g., 80% of max connections)
8. Metrics are exposed for current usage / max for each bounded resource (see US-015)

## Technical Notes

- Bounded caches: use Caffeine (`spring.cache.caffeine...` or `Caffeine.newBuilder().maximumSize(100).expireAfterWrite(1, TimeUnit.HOURS).build()`)
- For `BasicCircuitBreaker.failureCounts`, use a `Cache` from Caffeine with `expireAfterWrite(5, TimeUnit.MINUTES)` instead of `ConcurrentHashMap`
- SSE connection limit: track in a wrapper around `registerConnection`/`unregisterConnection` with an `AtomicInteger` counter

## Risk

🟡 **MEDIUM** — Unbounded maps, caches, and queues are a silent memory leak. An attacker can craft requests to exhaust heap or Redis memory.

## Dependencies

US-012 (SSE sink leak fix), US-029 (dead-letter list limits)
