CREATE TABLE wallet_funding_events (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    funding_reference VARCHAR(255) NOT NULL UNIQUE,
    administrator_user_id BIGINT NOT NULL REFERENCES app_users (id),
    beneficiary_user_id BIGINT NOT NULL REFERENCES app_users (id),
    amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
    currency VARCHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    request_fingerprint CHAR(64) NOT NULL,
    wallet_public_id UUID REFERENCES wallets (public_id),
    ledger_public_id UUID UNIQUE REFERENCES wallet_ledger (public_id),
    balance_after_minor BIGINT CHECK (balance_after_minor >= 0),
    wallet_version BIGINT CHECK (wallet_version >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    audit_record_id BIGINT REFERENCES audit_records (id),
    CONSTRAINT wallet_funding_result_chk CHECK (
        (completed_at IS NULL AND num_nonnulls(wallet_public_id, ledger_public_id, balance_after_minor, wallet_version, audit_record_id) = 0)
        OR (completed_at IS NOT NULL AND num_nonnulls(wallet_public_id, ledger_public_id, balance_after_minor, wallet_version, audit_record_id) = 5)
    )
);

CREATE FUNCTION protect_completed_wallet_funding() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' OR OLD.completed_at IS NOT NULL THEN
        RAISE EXCEPTION 'wallet funding evidence is immutable';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER wallet_funding_immutable_trigger
    BEFORE UPDATE OR DELETE ON wallet_funding_events
    FOR EACH ROW EXECUTE FUNCTION protect_completed_wallet_funding();
