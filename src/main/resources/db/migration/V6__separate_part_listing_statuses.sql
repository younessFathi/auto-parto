ALTER TABLE part_listings
    ADD COLUMN business_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    ADD COLUMN delivery_status VARCHAR(32) NOT NULL DEFAULT 'NOT_READY',
    ADD COLUMN storage_status VARCHAR(32) NOT NULL DEFAULT 'NOT_STORED';

UPDATE part_listings SET business_status = status;

ALTER TABLE part_listings
    ADD CONSTRAINT part_listings_business_status_chk CHECK (business_status IN ('DRAFT', 'PUBLISHED', 'PAUSED', 'RESERVED', 'CLOSED')),
    ADD CONSTRAINT part_listings_delivery_status_chk CHECK (delivery_status IN ('NOT_READY', 'READY', 'DISPATCHED', 'DELIVERED', 'FAILED')),
    ADD CONSTRAINT part_listings_storage_status_chk CHECK (storage_status IN ('NOT_STORED', 'STAGED', 'STORED', 'DELETED'));

CREATE INDEX part_listings_seller_business_created_idx
    ON part_listings (seller_user_id, business_status, created_at DESC, public_id DESC);
