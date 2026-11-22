-- Conflicting legacy accepted offers must be reconciled explicitly before upgrade.
CREATE UNIQUE INDEX offers_one_accepted_request_idx ON offers (part_request_id)
    WHERE part_request_id IS NOT NULL AND status = 'ACCEPTED';
CREATE UNIQUE INDEX offers_one_accepted_listing_idx ON offers (part_listing_id)
    WHERE part_listing_id IS NOT NULL AND status = 'ACCEPTED';

CREATE TABLE offer_acceptances (
    offer_id BIGINT PRIMARY KEY REFERENCES offers (id),
    public_id UUID NOT NULL UNIQUE,
    deciding_user_id BIGINT NOT NULL REFERENCES app_users (id),
    original_version BIGINT NOT NULL CHECK (original_version >= 0),
    buyer_notification_id BIGINT NOT NULL UNIQUE REFERENCES notifications (id),
    seller_notification_id BIGINT NOT NULL UNIQUE REFERENCES notifications (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (buyer_notification_id <> seller_notification_id)
);
CREATE TRIGGER offer_acceptances_immutable_trigger BEFORE UPDATE OR DELETE ON offer_acceptances
    FOR EACH ROW EXECUTE FUNCTION prevent_immutable_record_mutation();
