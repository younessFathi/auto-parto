CREATE INDEX files_pending_recovery_idx
    ON files (created_at, public_id)
    WHERE status = 'PENDING';
