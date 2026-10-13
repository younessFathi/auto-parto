BEGIN;

DO $$
DECLARE
    wallet_balance BIGINT;
    ledger_balance BIGINT;
BEGIN
    IF (SELECT count(*) FROM app_users WHERE key_version = 'dev-fixture-v1') <> 2
       OR (SELECT count(*) FROM buyer_profiles WHERE public_id = '00000000-0000-4000-8000-000000000201') <> 1
       OR (SELECT count(*) FROM seller_profiles WHERE public_id = '00000000-0000-4000-8000-000000000202') <> 1
       OR (SELECT count(*) FROM part_requests WHERE public_id = '00000000-0000-4000-8000-000000000301') <> 1
       OR (SELECT count(*) FROM part_listings WHERE public_id = '00000000-0000-4000-8000-000000000302') <> 1
       OR (SELECT count(*) FROM offers WHERE public_id = '00000000-0000-4000-8000-000000000303') <> 1
       OR (SELECT count(*) FROM wallets WHERE public_id = '00000000-0000-4000-8000-000000000304') <> 1
       OR (SELECT count(*) FROM wallet_ledger WHERE public_id = '00000000-0000-4000-8000-000000000305') <> 1
       OR (SELECT count(*) FROM device_tokens WHERE public_id = '00000000-0000-4000-8000-000000000306') <> 1 THEN
        RAISE EXCEPTION 'development fixture reconciliation failed: required records are missing or duplicated';
    END IF;

    IF (SELECT count(*) FROM notification_preferences WHERE public_id = '00000000-0000-4000-8000-000000000307') <> 1 THEN
        RAISE EXCEPTION 'development fixture reconciliation failed: required records are missing or duplicated';
    END IF;

    -- No local file import is selected. File metadata without an object-storage
    -- manifest must stop the cutover rather than silently losing references.
    IF (SELECT count(*) FROM files) <> 0 THEN
        RAISE EXCEPTION 'development fixture reconciliation failed: file metadata exists without an object-storage manifest';
    END IF;

    SELECT balance_minor INTO wallet_balance
    FROM wallets WHERE public_id = '00000000-0000-4000-8000-000000000304';

    SELECT COALESCE(SUM(amount_minor), 0) INTO ledger_balance
    FROM wallet_ledger ledger
    JOIN wallets wallet ON wallet.id = ledger.wallet_id
    WHERE wallet.public_id = '00000000-0000-4000-8000-000000000304';

    IF wallet_balance IS DISTINCT FROM ledger_balance THEN
        RAISE EXCEPTION 'development fixture reconciliation failed: wallet balance does not equal ledger sum';
    END IF;
END;
$$;

ROLLBACK;
