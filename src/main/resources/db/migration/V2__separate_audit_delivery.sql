ALTER TABLE outbox_events
    ADD COLUMN aggregate_public_id UUID;

UPDATE outbox_events event
SET aggregate_public_id = CASE event.aggregate_type
    WHEN 'app_user' THEN (SELECT public_id FROM app_users WHERE id = event.aggregate_id)
    WHEN 'buyer_profile' THEN (SELECT public_id FROM buyer_profiles WHERE id = event.aggregate_id)
    WHEN 'seller_profile' THEN (SELECT public_id FROM seller_profiles WHERE id = event.aggregate_id)
    WHEN 'part_request' THEN (SELECT public_id FROM part_requests WHERE id = event.aggregate_id)
    WHEN 'part_listing' THEN (SELECT public_id FROM part_listings WHERE id = event.aggregate_id)
    WHEN 'offer' THEN (SELECT public_id FROM offers WHERE id = event.aggregate_id)
    WHEN 'wallet' THEN (SELECT public_id FROM wallets WHERE id = event.aggregate_id)
    WHEN 'otp_challenge' THEN (SELECT public_id FROM otp_challenges WHERE id = event.aggregate_id)
    WHEN 'file' THEN (SELECT public_id FROM files WHERE id = event.aggregate_id)
    WHEN 'device_token' THEN (SELECT public_id FROM device_tokens WHERE id = event.aggregate_id)
    WHEN 'notification' THEN (SELECT public_id FROM notifications WHERE id = event.aggregate_id)
    ELSE NULL
END
WHERE event.aggregate_id IS NOT NULL AND event.aggregate_public_id IS NULL;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM outbox_events
        WHERE aggregate_id IS NOT NULL AND aggregate_public_id IS NULL
    ) THEN
        RAISE EXCEPTION 'outbox events with aggregate IDs require a resolvable aggregate public ID';
    END IF;
END;
$$;

ALTER TABLE outbox_events
    ADD CONSTRAINT outbox_events_aggregate_public_chk
    CHECK (aggregate_id IS NULL OR aggregate_public_id IS NOT NULL);

CREATE INDEX outbox_events_aggregate_public_idx
    ON outbox_events (aggregate_type, aggregate_public_id, created_at);

COMMENT ON COLUMN outbox_events.created_at IS
    'Authoritative occurrence timestamp for the durable event contract; immutable after insertion.';

CREATE TABLE outbox_deliveries (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    outbox_event_id BIGINT NOT NULL REFERENCES outbox_events (id),
    destination VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    available_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lease_owner VARCHAR(255),
    lease_expires_at TIMESTAMPTZ,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(64),
    delivered_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT outbox_deliveries_destination_chk CHECK (
        destination IN ('AUDIT', 'SSE', 'FCM', 'EMAIL')
    ),
    CONSTRAINT outbox_deliveries_status_chk CHECK (
        status IN ('PENDING', 'CLAIMED', 'DISPATCHED', 'EXHAUSTED')
    ),
    CONSTRAINT outbox_deliveries_attempts_chk CHECK (attempt_count >= 0),
    CONSTRAINT outbox_deliveries_error_chk CHECK (
        last_error IS NULL OR last_error IN (
            'AUDIT_DELIVERY_RETRYABLE', 'AUDIT_DELIVERY_PERMANENT', 'AUDIT_DELIVERY_UNKNOWN'
        )
    ),
    CONSTRAINT outbox_deliveries_lease_chk CHECK (
        (status = 'CLAIMED' AND lease_owner IS NOT NULL AND lease_expires_at IS NOT NULL)
        OR (status <> 'CLAIMED' AND lease_owner IS NULL AND lease_expires_at IS NULL)
    ),
    CONSTRAINT outbox_deliveries_delivery_time_chk CHECK (
        (status = 'DISPATCHED' AND delivered_at IS NOT NULL)
        OR (status <> 'DISPATCHED' AND delivered_at IS NULL)
    ),
    CONSTRAINT outbox_deliveries_event_destination_uidx UNIQUE (outbox_event_id, destination)
);

CREATE INDEX outbox_deliveries_claim_idx
    ON outbox_deliveries (destination, status, available_at, lease_expires_at);
CREATE INDEX outbox_deliveries_event_idx
    ON outbox_deliveries (outbox_event_id, destination);

CREATE FUNCTION create_audit_delivery_for_outbox_event()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    INSERT INTO outbox_deliveries (public_id, outbox_event_id, destination, available_at)
    VALUES (gen_random_uuid(), NEW.id, 'AUDIT', NEW.available_at)
    ON CONFLICT (outbox_event_id, destination) DO NOTHING;
    RETURN NEW;
END;
$$;

INSERT INTO outbox_deliveries (public_id, outbox_event_id, destination, available_at)
SELECT gen_random_uuid(), id, 'AUDIT', available_at
FROM outbox_events
ON CONFLICT (outbox_event_id, destination) DO NOTHING;

CREATE TRIGGER outbox_events_audit_delivery_trigger
    AFTER INSERT ON outbox_events
    FOR EACH ROW EXECUTE FUNCTION create_audit_delivery_for_outbox_event();

CREATE TRIGGER outbox_deliveries_updated_at_trigger
    BEFORE UPDATE ON outbox_deliveries
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
