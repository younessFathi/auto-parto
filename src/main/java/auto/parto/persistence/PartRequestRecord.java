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
        Instant updatedAt) {
}
