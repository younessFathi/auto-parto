DROP INDEX IF EXISTS part_listings_matching_category_idx;
DROP INDEX IF EXISTS part_listings_matching_vehicle_idx;
DROP INDEX IF EXISTS part_listings_status_created_idx;

CREATE INDEX part_listings_matching_order_idx
    ON part_listings (created_at DESC, public_id DESC)
    WHERE status = 'PUBLISHED';

CREATE INDEX part_listings_matching_condition_idx
    ON part_listings (condition, created_at DESC, public_id DESC)
    WHERE status = 'PUBLISHED';

CREATE INDEX part_listings_matching_vehicle_idx
    ON part_listings (LOWER(vehicle_make), LOWER(vehicle_model), vehicle_year,
                      created_at DESC, public_id DESC)
    WHERE status = 'PUBLISHED';

CREATE INDEX part_listings_matching_category_condition_idx
    ON part_listings (category, condition, created_at DESC, public_id DESC)
    WHERE status = 'PUBLISHED';

CREATE INDEX part_listings_matching_full_idx
    ON part_listings (category, condition, LOWER(vehicle_make), LOWER(vehicle_model), vehicle_year,
                      created_at DESC, public_id DESC)
    WHERE status = 'PUBLISHED';
