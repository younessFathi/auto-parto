CREATE INDEX part_requests_buyer_created_public_idx
    ON part_requests (buyer_user_id, created_at DESC, public_id DESC);
