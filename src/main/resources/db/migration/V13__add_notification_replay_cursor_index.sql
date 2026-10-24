CREATE INDEX notifications_recipient_replay_cursor_idx
    ON notifications (recipient_user_id, created_at DESC, public_id DESC);
