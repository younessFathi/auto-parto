CREATE TABLE notification_preferences (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    user_id BIGINT NOT NULL UNIQUE REFERENCES app_users (id),
    email_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    push_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    sms_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    new_offer_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    price_alert_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    search_match_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    order_update_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    marketing_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX notification_preferences_user_idx ON notification_preferences (user_id);
