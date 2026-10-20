# Device-token persistence

When the PostgreSQL device-token boundary is enabled, `device_tokens` is the authoritative store. Each record contains the canonical owner, provider token, normalized platform, active/invalid status, creation and last-seen timestamps, and invalidation time. The database uniqueness constraint on `provider_token` prevents two records from representing the same provider token.

Registration derives the owner from the authenticated canonical principal. Repeating registration for the same owner updates the platform and last-seen time in one transaction. A token already owned by another user produces a safe conflict and cannot be reassigned by registration.

Redis stores only rebuildable delivery projections. PostgreSQL is updated before invalid-token cleanup is attempted in Redis, and Redis failures do not undo the durable PostgreSQL state. `DeviceTokenService.rebuildRedisProjection(limit)` rebuilds a bounded projection from active PostgreSQL records after Redis loss; callers must invoke it in bounded batches as part of the Redis recovery procedure.
