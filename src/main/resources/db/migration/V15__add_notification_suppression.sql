ALTER TABLE notifications DROP CONSTRAINT notifications_status_chk;
ALTER TABLE notifications ADD CONSTRAINT notifications_status_chk
    CHECK (status IN ('PENDING', 'DISPATCHED', 'EXHAUSTED', 'SUPPRESSED'));
ALTER TABLE notification_attempts DROP CONSTRAINT notification_attempts_status_chk;
ALTER TABLE notification_attempts ADD CONSTRAINT notification_attempts_status_chk CHECK (
    (channel = 'SSE' AND status IN ('WRITTEN', 'FAILED', 'EXPIRED', 'SUPPRESSED'))
    OR (channel = 'FCM' AND status IN ('ACCEPTED', 'REJECTED', 'INVALID_TOKEN', 'FAILED', 'SUPPRESSED'))
    OR (channel = 'EMAIL' AND status IN ('ACCEPTED', 'REJECTED', 'FAILED', 'SUPPRESSED'))
);
