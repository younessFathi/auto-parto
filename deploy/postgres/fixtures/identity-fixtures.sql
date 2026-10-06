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
    'GENERAL',
    'PostgreSQL development fixture'
FROM app_users
WHERE public_id = '00000000-0000-4000-8000-000000000102'
ON CONFLICT DO NOTHING;

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
          AND profile.part_type = 'GENERAL'
          AND profile.description = 'PostgreSQL development fixture'
    ) THEN
        RAISE EXCEPTION 'development seller profile fixture conflicts with existing data';
    END IF;
END;
$$;

COMMIT;
