BEGIN;

INSERT INTO app_users (public_id, email, role, status, key_version)
VALUES
    ('00000000-0000-4000-8000-000000000101', 'dev-buyer@auto-parto.test', 'BUYER', 'ACTIVE', 'dev-fixture-v1'),
    ('00000000-0000-4000-8000-000000000102', 'dev-seller@auto-parto.test', 'SELLER', 'ACTIVE', 'dev-fixture-v1')
ON CONFLICT DO NOTHING;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM app_users
        WHERE public_id = '00000000-0000-4000-8000-000000000101'
          AND email = 'dev-buyer@auto-parto.test'
          AND role = 'BUYER'
          AND status = 'ACTIVE'
          AND key_version = 'dev-fixture-v1'
    ) THEN
        RAISE EXCEPTION 'development buyer fixture conflicts with existing identity data';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM app_users
        WHERE public_id = '00000000-0000-4000-8000-000000000102'
          AND email = 'dev-seller@auto-parto.test'
          AND role = 'SELLER'
          AND status = 'ACTIVE'
          AND key_version = 'dev-fixture-v1'
    ) THEN
        RAISE EXCEPTION 'development seller fixture conflicts with existing identity data';
    END IF;
END;
$$;

INSERT INTO buyer_profiles (public_id, user_id, first_name, last_name, phone_number)
SELECT
    '00000000-0000-4000-8000-000000000201',
    id,
    'Development',
    'Buyer',
    '+212600000101'
FROM app_users
WHERE public_id = '00000000-0000-4000-8000-000000000101'
ON CONFLICT DO NOTHING;

INSERT INTO seller_profiles (public_id, user_id, first_name, last_name, phone_number, part_type, description)
SELECT
    '00000000-0000-4000-8000-000000000202',
    id,
    'Development',
    'Seller',
    '+212600000102',
    'BOTH',
    'PostgreSQL development fixture'
FROM app_users
WHERE public_id = '00000000-0000-4000-8000-000000000102'
ON CONFLICT DO NOTHING;

INSERT INTO part_requests (public_id, buyer_user_id, name, description, condition, category, status)
SELECT
    '00000000-0000-4000-8000-000000000301',
    id,
    'Development brake request',
    'PostgreSQL development marketplace fixture',
    'NEW',
    'CAR',
    'OPEN'
FROM app_users
WHERE public_id = '00000000-0000-4000-8000-000000000101'
ON CONFLICT DO NOTHING;

INSERT INTO part_listings (public_id, seller_user_id, name, description, condition, category,
                           amount_minor, currency, status)
SELECT
    '00000000-0000-4000-8000-000000000302',
    id,
    'Development brake listing',
    'PostgreSQL development marketplace fixture',
    'NEW',
    'CAR',
    12500,
    'MAD',
    'PUBLISHED'
FROM app_users
WHERE public_id = '00000000-0000-4000-8000-000000000102'
ON CONFLICT DO NOTHING;

INSERT INTO offers (public_id, part_request_id, buyer_user_id, seller_user_id,
                    amount_minor, currency, status, negotiable, delivery_amount_minor, delivery_mode)
SELECT
    '00000000-0000-4000-8000-000000000303',
    request.id,
    buyer.id,
    seller.id,
    12500,
    'MAD',
    'PENDING',
    FALSE,
    500,
    'DELIVERY'
FROM part_requests request
JOIN app_users buyer ON buyer.id = request.buyer_user_id
JOIN app_users seller ON seller.public_id = '00000000-0000-4000-8000-000000000102'
WHERE request.public_id = '00000000-0000-4000-8000-000000000301'
  AND buyer.public_id = '00000000-0000-4000-8000-000000000101'
ON CONFLICT DO NOTHING;

INSERT INTO wallets (public_id, owner_user_id, balance_minor, currency)
SELECT
    '00000000-0000-4000-8000-000000000304',
    id,
    0,
    'MAD'
FROM app_users
WHERE public_id = '00000000-0000-4000-8000-000000000102'
ON CONFLICT DO NOTHING;

INSERT INTO device_tokens (public_id, owner_user_id, provider_token, platform, status)
SELECT
    '00000000-0000-4000-8000-000000000306',
    id,
    'development-device-token-v1',
    'ANDROID',
    'ACTIVE'
FROM app_users
WHERE public_id = '00000000-0000-4000-8000-000000000101'
ON CONFLICT DO NOTHING;

INSERT INTO notification_preferences (public_id, user_id, email_enabled, push_enabled, sms_enabled,
                                      new_offer_enabled, price_alert_enabled, search_match_enabled,
                                      order_update_enabled, marketing_enabled)
SELECT
    '00000000-0000-4000-8000-000000000307',
    id,
    TRUE,
    TRUE,
    FALSE,
    TRUE,
    TRUE,
    TRUE,
    TRUE,
    FALSE
FROM app_users
WHERE public_id = '00000000-0000-4000-8000-000000000101'
ON CONFLICT DO NOTHING;

DO $$
DECLARE
    wallet_id BIGINT;
    owner_id BIGINT;
    ledger_id BIGINT;
    audit_id BIGINT;
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM wallet_ledger
        WHERE public_id = '00000000-0000-4000-8000-000000000305'
    ) THEN
        SELECT wallet.id, wallet.owner_user_id
        INTO wallet_id, owner_id
        FROM wallets wallet
        WHERE wallet.public_id = '00000000-0000-4000-8000-000000000304';

        IF wallet_id IS NULL THEN
            RAISE EXCEPTION 'development wallet fixture was not created';
        END IF;

        SELECT nextval('wallet_ledger_id_seq') INTO ledger_id;
        INSERT INTO audit_records (public_id, actor_user_id, action, entity_type, entity_id)
        VALUES (gen_random_uuid(), owner_id, 'CREATE', 'wallet_ledger', ledger_id)
        RETURNING id INTO audit_id;

        INSERT INTO wallet_ledger (id, public_id, wallet_id, amount_minor, balance_after_minor,
                                   currency, operation, external_reference, idempotency_key, audit_record_id)
        VALUES (ledger_id, '00000000-0000-4000-8000-000000000305', wallet_id,
                12500, 12500, 'MAD', 'CREDIT', 'development-fixture',
                'development-wallet-credit-v1', audit_id);

        UPDATE wallets SET balance_minor = 12500
        WHERE id = wallet_id AND balance_minor = 0;
    END IF;
END;
$$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM buyer_profiles profile
        JOIN app_users app_user ON app_user.id = profile.user_id
        WHERE profile.public_id = '00000000-0000-4000-8000-000000000201'
          AND app_user.public_id = '00000000-0000-4000-8000-000000000101'
          AND profile.first_name = 'Development'
          AND profile.last_name = 'Buyer'
          AND profile.phone_number = '+212600000101'
    ) THEN
        RAISE EXCEPTION 'development buyer profile fixture conflicts with existing data';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM seller_profiles profile
        JOIN app_users app_user ON app_user.id = profile.user_id
        WHERE profile.public_id = '00000000-0000-4000-8000-000000000202'
          AND app_user.public_id = '00000000-0000-4000-8000-000000000102'
          AND profile.first_name = 'Development'
          AND profile.last_name = 'Seller'
          AND profile.phone_number = '+212600000102'
          AND profile.part_type = 'BOTH'
          AND profile.description = 'PostgreSQL development fixture'
    ) THEN
        RAISE EXCEPTION 'development seller profile fixture conflicts with existing data';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM part_requests request
        JOIN app_users app_user ON app_user.id = request.buyer_user_id
        WHERE request.public_id = '00000000-0000-4000-8000-000000000301'
          AND app_user.public_id = '00000000-0000-4000-8000-000000000101'
          AND request.name = 'Development brake request'
          AND request.condition = 'NEW'
          AND request.category = 'CAR'
          AND request.status = 'OPEN'
    ) THEN
        RAISE EXCEPTION 'development part request fixture conflicts with existing data';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM part_listings listing
        JOIN app_users app_user ON app_user.id = listing.seller_user_id
        WHERE listing.public_id = '00000000-0000-4000-8000-000000000302'
          AND app_user.public_id = '00000000-0000-4000-8000-000000000102'
          AND listing.name = 'Development brake listing'
          AND listing.amount_minor = 12500
          AND listing.currency = 'MAD'
          AND listing.status = 'PUBLISHED'
    ) THEN
        RAISE EXCEPTION 'development part listing fixture conflicts with existing data';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM offers offer
        JOIN part_requests request ON request.id = offer.part_request_id
        WHERE offer.public_id = '00000000-0000-4000-8000-000000000303'
          AND request.public_id = '00000000-0000-4000-8000-000000000301'
          AND offer.amount_minor = 12500
          AND offer.currency = 'MAD'
          AND offer.status = 'PENDING'
    ) THEN
        RAISE EXCEPTION 'development offer fixture conflicts with existing data';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM wallets wallet
        JOIN app_users app_user ON app_user.id = wallet.owner_user_id
        WHERE wallet.public_id = '00000000-0000-4000-8000-000000000304'
          AND app_user.public_id = '00000000-0000-4000-8000-000000000102'
          AND wallet.balance_minor = 12500
          AND wallet.currency = 'MAD'
    ) THEN
        RAISE EXCEPTION 'development wallet fixture conflicts with existing data';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM wallet_ledger
        WHERE public_id = '00000000-0000-4000-8000-000000000305'
          AND amount_minor = 12500
          AND balance_after_minor = 12500
          AND idempotency_key = 'development-wallet-credit-v1'
    ) THEN
        RAISE EXCEPTION 'development wallet ledger fixture conflicts with existing data';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM device_tokens token
        JOIN app_users app_user ON app_user.id = token.owner_user_id
        WHERE token.public_id = '00000000-0000-4000-8000-000000000306'
          AND app_user.public_id = '00000000-0000-4000-8000-000000000101'
          AND token.provider_token = 'development-device-token-v1'
          AND token.platform = 'ANDROID'
          AND token.status = 'ACTIVE'
    ) THEN
        RAISE EXCEPTION 'development device token fixture conflicts with existing data';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM notification_preferences preferences
        JOIN app_users app_user ON app_user.id = preferences.user_id
        WHERE preferences.public_id = '00000000-0000-4000-8000-000000000307'
          AND app_user.public_id = '00000000-0000-4000-8000-000000000101'
          AND preferences.email_enabled
          AND preferences.push_enabled
          AND NOT preferences.sms_enabled
          AND NOT preferences.marketing_enabled
    ) THEN
        RAISE EXCEPTION 'development notification preferences fixture conflicts with existing data';
    END IF;
END;
$$;

COMMIT;
