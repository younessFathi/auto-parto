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
        Condition condition,
        Category category,
        long amountMinor,
        String currency,
        String status,
        long viewCount,
        Instant createdAt,
        Instant updatedAt) {
}
