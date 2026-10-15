package auto.parto.persistence;

import auto.parto.enums.Category;
import auto.parto.enums.Condition;

import java.time.Instant;
import java.util.UUID;

public record PartListingRecord(
        UUID publicId,
        UUID sellerUserPublicId,
        String name,
        String description,
        String vehicleMake,
        String vehicleModel,
        Integer vehicleYear,
        Condition condition,
        Category category,
        long amountMinor,
        String currency,
        String status,
        String businessStatus,
        String deliveryStatus,
        String storageStatus,
        long viewCount,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    public PartListingRecord(UUID publicId, UUID sellerUserPublicId, String name, String description,
                             Condition condition, Category category, long amountMinor, String currency,
                             String status, long viewCount, Instant createdAt, Instant updatedAt) {
        this(publicId, sellerUserPublicId, name, description, null, null, null, condition, category, amountMinor, currency,
                status, status, "NOT_READY", "NOT_STORED", viewCount, createdAt, updatedAt, 0);
    }
}
