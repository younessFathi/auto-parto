-- Supports bounded keyset scans used to rebuild the Redis device-token projection.
CREATE INDEX device_tokens_active_recovery_idx
    ON device_tokens (last_seen_at DESC, public_id DESC)
    WHERE status = 'ACTIVE';
