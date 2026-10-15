# Seller Part Listings API

Seller listing endpoints derive ownership from the canonical authenticated seller principal.

## Create

`POST /api/v1/part-listings` with `name`, optional description and vehicle fields, `amountMinor`, and an uppercase three-letter `currency`. New listings start with `businessStatus=DRAFT`, `deliveryStatus=NOT_READY`, and `storageStatus=NOT_STORED`.

## List

`GET /api/v1/part-listings?page=0&size=50` lists the authenticated seller's listings. `size` is bounded to 1–100.

## Lifecycle

`POST /api/v1/part-listings/{publicId}/transition?status=PUBLISHED` changes the business lifecycle. Valid transitions are `DRAFT -> PUBLISHED|CLOSED`, `PUBLISHED -> PAUSED|RESERVED|CLOSED`, `PAUSED -> PUBLISHED|CLOSED`, and `RESERVED -> CLOSED`. Invalid transitions return `409`.

Business, delivery, and storage statuses are separate fields and are not inferred from one another.
