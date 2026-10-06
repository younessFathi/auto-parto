WITH ranked_active_login_challenges AS MATERIALIZED (
    SELECT
        oc.id,
        oc.user_id,
        ROW_NUMBER() OVER (
            PARTITION BY oc.user_id
            ORDER BY oc.created_at DESC, oc.id DESC
        ) AS challenge_rank
    FROM otp_challenges oc
    WHERE oc.purpose = 'LOGIN'
      AND oc.consumed_at IS NULL
      AND oc.user_id IS NOT NULL
), duplicate_audits AS (
    INSERT INTO audit_records
        (public_id, actor_user_id, action, entity_type, entity_id)
    SELECT gen_random_uuid(), user_id, 'UPDATE', 'otp_challenges', id
    FROM ranked_active_login_challenges
    WHERE challenge_rank > 1
    RETURNING id AS audit_id, entity_id
)
UPDATE otp_challenges oc
SET consumed_at = CURRENT_TIMESTAMP,
    last_audit_record_id = duplicate_audits.audit_id
FROM duplicate_audits
WHERE oc.id = duplicate_audits.entity_id;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM otp_challenges
        WHERE purpose = 'LOGIN'
          AND consumed_at IS NULL
          AND user_id IS NOT NULL
        GROUP BY user_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'cannot enforce one active LOGIN OTP challenge per user';
    END IF;
END;
$$;

CREATE UNIQUE INDEX otp_challenges_active_login_user_uidx
    ON otp_challenges (user_id)
    WHERE purpose = 'LOGIN'
      AND consumed_at IS NULL
      AND user_id IS NOT NULL;
