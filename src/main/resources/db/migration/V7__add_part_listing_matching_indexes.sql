CREATE INDEX part_listings_matching_category_idx
    ON part_listings (status, category, condition, created_at DESC, public_id DESC);

CREATE INDEX part_listings_matching_vehicle_idx
    ON part_listings (status, category, condition, LOWER(vehicle_make), LOWER(vehicle_model),
                      vehicle_year, created_at DESC, public_id DESC);
