ALTER TABLE notifications
    ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN lease_owner VARCHAR(255),
    ADD COLUMN lease_expires_at TIMESTAMPTZ,
    ADD COLUMN last_failure_code VARCHAR(64);

ALTER TABLE notifications
    ADD CONSTRAINT notifications_attempt_count_chk CHECK (attempt_count >= 0),
    ADD CONSTRAINT notifications_lease_chk CHECK (
        (lease_owner IS NULL AND lease_expires_at IS NULL)
        OR (lease_owner IS NOT NULL AND lease_expires_at IS NOT NULL)
    );

CREATE INDEX notifications_delivery_claim_idx
    ON notifications (status, next_attempt_at, lease_expires_at, created_at, id)
    WHERE status = 'PENDING';
