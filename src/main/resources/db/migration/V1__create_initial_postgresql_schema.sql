CREATE TABLE audit_records (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    actor_user_id BIGINT,
    action VARCHAR(128) NOT NULL,
    entity_type VARCHAR(128) NOT NULL,
    entity_id BIGINT,
    correlation_id UUID,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX audit_records_entity_idx ON audit_records (entity_type, entity_id, created_at DESC);
CREATE INDEX audit_records_actor_idx ON audit_records (actor_user_id, created_at DESC);

CREATE TABLE app_users (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    provider VARCHAR(64),
    provider_subject VARCHAR(255),
    email VARCHAR(320),
    role VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    key_version VARCHAR(64),
    created_audit_record_id BIGINT NOT NULL REFERENCES audit_records (id),
    last_audit_record_id BIGINT NOT NULL REFERENCES audit_records (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT app_users_role_chk CHECK (role IN ('BUYER', 'SELLER', 'ADMIN')),
    CONSTRAINT app_users_status_chk CHECK (status IN ('ACTIVE', 'INACTIVE', 'LOCKED')),
    CONSTRAINT app_users_identity_pair_chk CHECK (
        (provider IS NULL AND provider_subject IS NULL)
        OR (provider IS NOT NULL AND provider_subject IS NOT NULL)
    ),
    CONSTRAINT app_users_identity_presence_chk CHECK (email IS NOT NULL OR provider_subject IS NOT NULL),
    CONSTRAINT app_users_id_role_uidx UNIQUE (id, role)
);

ALTER TABLE audit_records
    ADD CONSTRAINT audit_records_actor_fk FOREIGN KEY (actor_user_id) REFERENCES app_users (id);

CREATE UNIQUE INDEX app_users_provider_subject_uidx
    ON app_users (provider, provider_subject)
    WHERE provider IS NOT NULL AND provider_subject IS NOT NULL;

CREATE UNIQUE INDEX app_users_email_uidx
    ON app_users (LOWER(email))
    WHERE email IS NOT NULL;

CREATE TABLE buyer_profiles (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    user_id BIGINT NOT NULL UNIQUE REFERENCES app_users (id),
    profile_role VARCHAR(32) NOT NULL DEFAULT 'BUYER',
    first_name VARCHAR(120),
    last_name VARCHAR(120),
    phone_number VARCHAR(40),
    picture_url TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT buyer_profiles_role_chk CHECK (profile_role = 'BUYER'),
    CONSTRAINT buyer_profiles_user_role_fk FOREIGN KEY (user_id, profile_role)
        REFERENCES app_users (id, role)
);

CREATE TABLE seller_profiles (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    user_id BIGINT NOT NULL UNIQUE REFERENCES app_users (id),
    profile_role VARCHAR(32) NOT NULL DEFAULT 'SELLER',
    first_name VARCHAR(120),
    last_name VARCHAR(120),
    phone_number VARCHAR(40),
    part_type VARCHAR(64),
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT seller_profiles_role_chk CHECK (profile_role = 'SELLER'),
    CONSTRAINT seller_profiles_user_role_fk FOREIGN KEY (user_id, profile_role)
        REFERENCES app_users (id, role)
);

CREATE FUNCTION prevent_multiple_role_profiles()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_TABLE_NAME = 'buyer_profiles'
       AND EXISTS (SELECT 1 FROM seller_profiles WHERE user_id = NEW.user_id) THEN
        RAISE EXCEPTION 'user cannot own both buyer and seller profiles';
    ELSIF TG_TABLE_NAME = 'seller_profiles'
       AND EXISTS (SELECT 1 FROM buyer_profiles WHERE user_id = NEW.user_id) THEN
        RAISE EXCEPTION 'user cannot own both buyer and seller profiles';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER buyer_profiles_role_exclusivity_trigger
    BEFORE INSERT OR UPDATE ON buyer_profiles
    FOR EACH ROW EXECUTE FUNCTION prevent_multiple_role_profiles();

CREATE TRIGGER seller_profiles_role_exclusivity_trigger
    BEFORE INSERT OR UPDATE ON seller_profiles
    FOR EACH ROW EXECUTE FUNCTION prevent_multiple_role_profiles();

CREATE TABLE addresses (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    owner_user_id BIGINT NOT NULL REFERENCES app_users (id),
    street VARCHAR(255) NOT NULL,
    city VARCHAR(120) NOT NULL,
    state VARCHAR(120),
    country VARCHAR(120) NOT NULL,
    postal_code VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX addresses_owner_idx ON addresses (owner_user_id, created_at DESC);

CREATE TABLE part_requests (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    buyer_user_id BIGINT NOT NULL REFERENCES app_users (id),
    buyer_role VARCHAR(32) NOT NULL DEFAULT 'BUYER',
    name VARCHAR(255) NOT NULL,
    description TEXT,
    condition VARCHAR(32),
    category VARCHAR(64),
    vehicle_make VARCHAR(120),
    vehicle_model VARCHAR(120),
    vehicle_year INTEGER,
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    view_count BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT part_requests_status_chk CHECK (status IN ('OPEN', 'MATCHED', 'CLOSED', 'CANCELLED')),
    CONSTRAINT part_requests_view_count_chk CHECK (view_count >= 0),
    CONSTRAINT part_requests_owner_uidx UNIQUE (id, buyer_user_id),
    CONSTRAINT part_requests_role_chk CHECK (buyer_role = 'BUYER'),
    CONSTRAINT part_requests_user_role_fk FOREIGN KEY (buyer_user_id, buyer_role)
        REFERENCES app_users (id, role)
);

CREATE INDEX part_requests_buyer_created_idx ON part_requests (buyer_user_id, created_at DESC);
CREATE INDEX part_requests_status_created_idx ON part_requests (status, created_at DESC);
CREATE INDEX part_requests_search_idx ON part_requests (LOWER(name), category, status);
CREATE INDEX part_requests_vehicle_search_idx
    ON part_requests (LOWER(vehicle_make), LOWER(vehicle_model), vehicle_year, status);

CREATE TABLE part_listings (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    seller_user_id BIGINT NOT NULL REFERENCES app_users (id),
    seller_role VARCHAR(32) NOT NULL DEFAULT 'SELLER',
    name VARCHAR(255) NOT NULL,
    description TEXT,
    condition VARCHAR(32),
    category VARCHAR(64),
    vehicle_make VARCHAR(120),
    vehicle_model VARCHAR(120),
    vehicle_year INTEGER,
    amount_minor BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    view_count BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT part_listings_amount_chk CHECK (amount_minor >= 0),
    CONSTRAINT part_listings_currency_chk CHECK (currency = UPPER(currency) AND currency ~ '^[A-Z]{3}$'),
    CONSTRAINT part_listings_status_chk CHECK (status IN ('DRAFT', 'PUBLISHED', 'PAUSED', 'RESERVED', 'CLOSED')),
    CONSTRAINT part_listings_view_count_chk CHECK (view_count >= 0),
    CONSTRAINT part_listings_owner_uidx UNIQUE (id, seller_user_id),
    CONSTRAINT part_listings_role_chk CHECK (seller_role = 'SELLER'),
    CONSTRAINT part_listings_user_role_fk FOREIGN KEY (seller_user_id, seller_role)
        REFERENCES app_users (id, role)
);

CREATE INDEX part_listings_seller_created_idx ON part_listings (seller_user_id, created_at DESC);
CREATE INDEX part_listings_status_created_idx ON part_listings (status, created_at DESC);
CREATE INDEX part_listings_search_idx ON part_listings (LOWER(name), category, status);
CREATE INDEX part_listings_vehicle_search_idx
    ON part_listings (LOWER(vehicle_make), LOWER(vehicle_model), vehicle_year, status);

CREATE TABLE offers (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    part_request_id BIGINT REFERENCES part_requests (id),
    part_listing_id BIGINT REFERENCES part_listings (id),
    buyer_user_id BIGINT NOT NULL REFERENCES app_users (id),
    buyer_role VARCHAR(32) NOT NULL DEFAULT 'BUYER',
    seller_user_id BIGINT NOT NULL REFERENCES app_users (id),
    seller_role VARCHAR(32) NOT NULL DEFAULT 'SELLER',
    amount_minor BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    negotiable BOOLEAN NOT NULL DEFAULT FALSE,
    delivery_amount_minor BIGINT NOT NULL DEFAULT 0,
    delivery_mode VARCHAR(32),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT offers_target_chk CHECK (num_nonnulls(part_request_id, part_listing_id) = 1),
    CONSTRAINT offers_amount_chk CHECK (amount_minor >= 0 AND delivery_amount_minor >= 0),
    CONSTRAINT offers_currency_chk CHECK (currency = UPPER(currency) AND currency ~ '^[A-Z]{3}$'),
    CONSTRAINT offers_status_chk CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT offers_buyer_role_chk CHECK (buyer_role = 'BUYER'),
    CONSTRAINT offers_seller_role_chk CHECK (seller_role = 'SELLER'),
    CONSTRAINT offers_buyer_user_role_fk FOREIGN KEY (buyer_user_id, buyer_role)
        REFERENCES app_users (id, role),
    CONSTRAINT offers_seller_user_role_fk FOREIGN KEY (seller_user_id, seller_role)
        REFERENCES app_users (id, role),
    CONSTRAINT offers_request_owner_fk FOREIGN KEY (part_request_id, buyer_user_id)
        REFERENCES part_requests (id, buyer_user_id),
    CONSTRAINT offers_listing_owner_fk FOREIGN KEY (part_listing_id, seller_user_id)
        REFERENCES part_listings (id, seller_user_id)
);

CREATE INDEX offers_request_created_idx ON offers (part_request_id, created_at DESC);
CREATE INDEX offers_listing_created_idx ON offers (part_listing_id, created_at DESC);
CREATE INDEX offers_buyer_status_idx ON offers (buyer_user_id, status);
CREATE INDEX offers_seller_status_idx ON offers (seller_user_id, status);

CREATE TABLE wallets (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    owner_user_id BIGINT NOT NULL REFERENCES app_users (id),
    balance_minor BIGINT NOT NULL DEFAULT 0,
    currency VARCHAR(3) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT wallets_balance_chk CHECK (balance_minor >= 0),
    CONSTRAINT wallets_currency_chk CHECK (currency = UPPER(currency) AND currency ~ '^[A-Z]{3}$'),
    CONSTRAINT wallets_owner_currency_uidx UNIQUE (owner_user_id, currency),
    CONSTRAINT wallets_id_currency_uidx UNIQUE (id, currency)
);

CREATE TABLE wallet_ledger (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    wallet_id BIGINT NOT NULL REFERENCES wallets (id),
    amount_minor BIGINT NOT NULL,
    balance_after_minor BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    operation VARCHAR(32) NOT NULL,
    external_reference VARCHAR(255),
    idempotency_key VARCHAR(255),
    audit_record_id BIGINT NOT NULL REFERENCES audit_records (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT wallet_ledger_amount_chk CHECK (amount_minor <> 0),
    CONSTRAINT wallet_ledger_balance_chk CHECK (balance_after_minor >= 0),
    CONSTRAINT wallet_ledger_currency_chk CHECK (currency = UPPER(currency) AND currency ~ '^[A-Z]{3}$'),
    CONSTRAINT wallet_ledger_operation_chk CHECK (operation IN ('CREDIT', 'DEBIT', 'REFUND', 'ADJUSTMENT')),
    CONSTRAINT wallet_ledger_operation_sign_chk CHECK (
        (operation IN ('CREDIT', 'REFUND') AND amount_minor > 0)
        OR (operation = 'DEBIT' AND amount_minor < 0)
        OR operation = 'ADJUSTMENT'
    ),
    CONSTRAINT wallet_ledger_wallet_currency_fk FOREIGN KEY (wallet_id, currency)
        REFERENCES wallets (id, currency)
);

CREATE UNIQUE INDEX wallet_ledger_idempotency_uidx
    ON wallet_ledger (wallet_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
CREATE INDEX wallet_ledger_wallet_created_idx ON wallet_ledger (wallet_id, created_at DESC);

CREATE TABLE otp_challenges (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    user_id BIGINT REFERENCES app_users (id),
    created_audit_record_id BIGINT NOT NULL REFERENCES audit_records (id),
    last_audit_record_id BIGINT NOT NULL REFERENCES audit_records (id),
    identity VARCHAR(320) NOT NULL,
    purpose VARCHAR(64) NOT NULL,
    code_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    failed_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT otp_challenges_attempts_chk CHECK (failed_attempts >= 0)
);

CREATE INDEX otp_challenges_identity_purpose_idx ON otp_challenges (LOWER(identity), purpose, created_at DESC);

CREATE TABLE files (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    owner_user_id BIGINT NOT NULL REFERENCES app_users (id),
    created_audit_record_id BIGINT NOT NULL REFERENCES audit_records (id),
    last_audit_record_id BIGINT NOT NULL REFERENCES audit_records (id),
    part_request_id BIGINT REFERENCES part_requests (id),
    part_listing_id BIGINT REFERENCES part_listings (id),
    object_key VARCHAR(1024) NOT NULL UNIQUE,
    original_filename VARCHAR(512) NOT NULL,
    content_type VARCHAR(255) NOT NULL,
    byte_size BIGINT NOT NULL,
    checksum VARCHAR(128),
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT files_target_chk CHECK (num_nonnulls(part_request_id, part_listing_id) = 1),
    CONSTRAINT files_size_chk CHECK (byte_size >= 0),
    CONSTRAINT files_status_chk CHECK (status IN ('PENDING', 'ACTIVE', 'TOMBSTONED')),
    CONSTRAINT files_request_owner_fk FOREIGN KEY (part_request_id, owner_user_id)
        REFERENCES part_requests (id, buyer_user_id),
    CONSTRAINT files_listing_owner_fk FOREIGN KEY (part_listing_id, owner_user_id)
        REFERENCES part_listings (id, seller_user_id)
);

CREATE INDEX files_owner_status_idx ON files (owner_user_id, status);
CREATE INDEX files_request_idx ON files (part_request_id) WHERE part_request_id IS NOT NULL;
CREATE INDEX files_listing_idx ON files (part_listing_id) WHERE part_listing_id IS NOT NULL;

CREATE TABLE device_tokens (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    owner_user_id BIGINT NOT NULL REFERENCES app_users (id),
    created_audit_record_id BIGINT NOT NULL REFERENCES audit_records (id),
    last_audit_record_id BIGINT NOT NULL REFERENCES audit_records (id),
    provider_token VARCHAR(1024) NOT NULL UNIQUE,
    platform VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    invalidated_at TIMESTAMPTZ,
    CONSTRAINT device_tokens_status_chk CHECK (status IN ('ACTIVE', 'INVALID', 'REVOKED')),
    CONSTRAINT device_tokens_platform_chk CHECK (platform IN ('ANDROID', 'IOS', 'WEB')),
    CONSTRAINT device_tokens_invalidated_at_chk CHECK (
        (status = 'ACTIVE' AND invalidated_at IS NULL)
        OR (status <> 'ACTIVE' AND invalidated_at IS NOT NULL)
    )
);

CREATE INDEX device_tokens_owner_status_idx ON device_tokens (owner_user_id, status);

CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    recipient_user_id BIGINT NOT NULL REFERENCES app_users (id),
    event_type VARCHAR(128) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT notifications_status_chk CHECK (status IN ('PENDING', 'DISPATCHED', 'EXHAUSTED'))
);

CREATE INDEX notifications_recipient_status_idx ON notifications (recipient_user_id, status, created_at DESC);

CREATE TABLE notification_attempts (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    notification_id BIGINT NOT NULL REFERENCES notifications (id),
    channel VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    provider_reference VARCHAR(255),
    failure_reason TEXT,
    attempted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    CONSTRAINT notification_attempts_channel_chk CHECK (channel IN ('SSE', 'FCM', 'EMAIL')),
    CONSTRAINT notification_attempts_status_chk CHECK (
        (channel = 'SSE' AND status IN ('WRITTEN', 'FAILED', 'EXPIRED'))
        OR (channel = 'FCM' AND status IN ('ACCEPTED', 'REJECTED', 'INVALID_TOKEN', 'FAILED'))
        OR (channel = 'EMAIL' AND status IN ('ACCEPTED', 'REJECTED', 'FAILED'))
    )
);

CREATE INDEX notification_attempts_notification_idx ON notification_attempts (notification_id, attempted_at DESC);

CREATE TABLE outbox_events (
    id BIGSERIAL PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    event_type VARCHAR(128) NOT NULL,
    schema_version INTEGER NOT NULL DEFAULT 1,
    aggregate_type VARCHAR(128) NOT NULL,
    aggregate_id BIGINT,
    payload JSONB NOT NULL,
    correlation_id UUID,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    available_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lease_owner VARCHAR(255),
    lease_expires_at TIMESTAMPTZ,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT outbox_events_status_chk CHECK (status IN ('PENDING', 'CLAIMED', 'DISPATCHED', 'EXHAUSTED')),
    CONSTRAINT outbox_events_attempts_chk CHECK (attempt_count >= 0),
    CONSTRAINT outbox_events_lease_chk CHECK (
        (status = 'CLAIMED' AND lease_owner IS NOT NULL AND lease_expires_at IS NOT NULL)
        OR (status <> 'CLAIMED' AND lease_owner IS NULL AND lease_expires_at IS NULL)
    )
);

CREATE INDEX outbox_events_claim_idx ON outbox_events (status, available_at, lease_expires_at);
CREATE INDEX outbox_events_aggregate_idx ON outbox_events (aggregate_type, aggregate_id, created_at);

CREATE TABLE idempotency_records (
    id BIGSERIAL PRIMARY KEY,
    owner_user_id BIGINT NOT NULL REFERENCES app_users (id),
    operation VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    request_fingerprint VARCHAR(128) NOT NULL,
    response_status INTEGER,
    response_body JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT idempotency_records_uidx UNIQUE (owner_user_id, operation, idempotency_key)
);

CREATE INDEX idempotency_records_expiry_idx ON idempotency_records (expires_at);

CREATE FUNCTION create_app_user_audit()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    audit_id BIGINT;
BEGIN
    INSERT INTO audit_records (public_id, action, entity_type, entity_id)
    VALUES (gen_random_uuid(), 'CREATE', TG_TABLE_NAME, NEW.id)
    RETURNING id INTO audit_id;

    NEW.created_audit_record_id = audit_id;
    NEW.last_audit_record_id = audit_id;
    RETURN NEW;
END;
$$;

CREATE FUNCTION create_otp_challenge_audit()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    audit_id BIGINT;
BEGIN
    INSERT INTO audit_records (public_id, actor_user_id, action, entity_type, entity_id)
    VALUES (gen_random_uuid(), NEW.user_id, 'CREATE', TG_TABLE_NAME, NEW.id)
    RETURNING id INTO audit_id;

    NEW.created_audit_record_id = audit_id;
    NEW.last_audit_record_id = audit_id;
    RETURN NEW;
END;
$$;

CREATE FUNCTION create_file_audit()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    audit_id BIGINT;
BEGIN
    INSERT INTO audit_records (public_id, actor_user_id, action, entity_type, entity_id)
    VALUES (gen_random_uuid(), NEW.owner_user_id, 'CREATE', TG_TABLE_NAME, NEW.id)
    RETURNING id INTO audit_id;

    NEW.created_audit_record_id = audit_id;
    NEW.last_audit_record_id = audit_id;
    RETURN NEW;
END;
$$;

CREATE FUNCTION create_device_token_audit()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    audit_id BIGINT;
BEGIN
    INSERT INTO audit_records (public_id, actor_user_id, action, entity_type, entity_id)
    VALUES (gen_random_uuid(), NEW.owner_user_id, 'CREATE', TG_TABLE_NAME, NEW.id)
    RETURNING id INTO audit_id;

    NEW.created_audit_record_id = audit_id;
    NEW.last_audit_record_id = audit_id;
    RETURN NEW;
END;
$$;

CREATE TRIGGER app_users_initial_audit_trigger
    BEFORE INSERT ON app_users
    FOR EACH ROW EXECUTE FUNCTION create_app_user_audit();
CREATE TRIGGER otp_challenges_initial_audit_trigger
    BEFORE INSERT ON otp_challenges
    FOR EACH ROW EXECUTE FUNCTION create_otp_challenge_audit();
CREATE TRIGGER files_initial_audit_trigger
    BEFORE INSERT ON files
    FOR EACH ROW EXECUTE FUNCTION create_file_audit();
CREATE TRIGGER device_tokens_initial_audit_trigger
    BEFORE INSERT ON device_tokens
    FOR EACH ROW EXECUTE FUNCTION create_device_token_audit();

CREATE FUNCTION set_updated_at()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$;

CREATE TRIGGER app_users_updated_at_trigger
    BEFORE UPDATE ON app_users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER buyer_profiles_updated_at_trigger
    BEFORE UPDATE ON buyer_profiles
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER seller_profiles_updated_at_trigger
    BEFORE UPDATE ON seller_profiles
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER addresses_updated_at_trigger
    BEFORE UPDATE ON addresses
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER part_requests_updated_at_trigger
    BEFORE UPDATE ON part_requests
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER part_listings_updated_at_trigger
    BEFORE UPDATE ON part_listings
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER offers_updated_at_trigger
    BEFORE UPDATE ON offers
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER wallets_updated_at_trigger
    BEFORE UPDATE ON wallets
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER files_updated_at_trigger
    BEFORE UPDATE ON files
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER notifications_updated_at_trigger
    BEFORE UPDATE ON notifications
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER outbox_events_updated_at_trigger
    BEFORE UPDATE ON outbox_events
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE FUNCTION require_sensitive_update_audit()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    business_changed BOOLEAN;
BEGIN
    IF NEW.created_audit_record_id IS DISTINCT FROM OLD.created_audit_record_id THEN
        RAISE EXCEPTION 'initial audit record cannot be changed';
    END IF;

    business_changed := (to_jsonb(OLD) - ARRAY['last_audit_record_id', 'updated_at'])
        IS DISTINCT FROM (to_jsonb(NEW) - ARRAY['last_audit_record_id', 'updated_at']);

    IF business_changed AND NEW.last_audit_record_id = OLD.last_audit_record_id THEN
        RAISE EXCEPTION 'sensitive record changes require a new audit record';
    END IF;

    IF NEW.last_audit_record_id IS DISTINCT FROM OLD.last_audit_record_id
       AND NOT EXISTS (
           SELECT 1
           FROM audit_records
           WHERE id = NEW.last_audit_record_id
             AND entity_type = TG_TABLE_NAME
             AND entity_id = NEW.id
       ) THEN
        RAISE EXCEPTION 'audit record does not identify the changed entity';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER app_users_audit_trigger
    BEFORE UPDATE ON app_users
    FOR EACH ROW EXECUTE FUNCTION require_sensitive_update_audit();
CREATE TRIGGER otp_challenges_audit_trigger
    BEFORE UPDATE ON otp_challenges
    FOR EACH ROW EXECUTE FUNCTION require_sensitive_update_audit();
CREATE TRIGGER files_audit_trigger
    BEFORE UPDATE ON files
    FOR EACH ROW EXECUTE FUNCTION require_sensitive_update_audit();
CREATE TRIGGER device_tokens_audit_trigger
    BEFORE UPDATE ON device_tokens
    FOR EACH ROW EXECUTE FUNCTION require_sensitive_update_audit();

CREATE FUNCTION prevent_immutable_record_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'immutable record cannot be changed or deleted';
END;
$$;

CREATE TRIGGER wallet_ledger_immutable_trigger
    BEFORE UPDATE OR DELETE ON wallet_ledger
    FOR EACH ROW EXECUTE FUNCTION prevent_immutable_record_mutation();

CREATE TRIGGER audit_records_immutable_trigger
    BEFORE UPDATE OR DELETE ON audit_records
    FOR EACH ROW EXECUTE FUNCTION prevent_immutable_record_mutation();
