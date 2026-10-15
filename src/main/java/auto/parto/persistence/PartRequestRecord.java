package auto.parto.persistence;

import auto.parto.enums.Category;
import auto.parto.enums.Condition;

import java.time.Instant;
import java.util.UUID;

/** PostgreSQL representation of a buyer part request. */
public record PartRequestRecord(
        UUID publicId,
        UUID buyerUserPublicId,
        String name,
        String description,
        Condition condition,
        Category category,
        String status,
        long viewCount,
        Instant createdAt,
        Instant updatedAt,
        String vehicleMake,
        String vehicleModel,
        Integer vehicleYear,
        long version) {

    public PartRequestRecord(UUID publicId, UUID buyerUserPublicId, String name, String description,
                             Condition condition, Category category, String status, long viewCount,
                             Instant createdAt, Instant updatedAt) {
        this(publicId, buyerUserPublicId, name, description, condition, category, status, viewCount,
                createdAt, updatedAt, null, null, null, 0);
    }

    public PartRequestRecord(UUID publicId, UUID buyerUserPublicId, String name, String description,
                             String condition, String category, String vehicleMake, String vehicleModel,
                             Integer vehicleYear, String status, Instant createdAt, Instant updatedAt,
                             long version) {
        this(publicId, buyerUserPublicId, name, description,
                condition == null ? null : Condition.valueOf(condition),
                category == null ? null : Category.valueOf(category), status, 0,
                createdAt, updatedAt, vehicleMake, vehicleModel, vehicleYear, version);
    }
}
