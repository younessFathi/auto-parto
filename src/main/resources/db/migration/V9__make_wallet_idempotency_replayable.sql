ALTER TABLE idempotency_records
    ADD COLUMN wallet_public_id UUID,
    ADD COLUMN ledger_public_id UUID,
    ADD COLUMN wallet_balance_after_minor BIGINT,
    ADD COLUMN wallet_currency VARCHAR(3),
    ADD COLUMN wallet_version BIGINT;

DROP INDEX IF EXISTS wallet_ledger_idempotency_uidx;

COMMENT ON TABLE idempotency_records IS
    'Owner and operation scoped request fingerprints and replay responses; expired rows may be purged.';
