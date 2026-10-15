package auto.parto.dto;

import java.time.Instant;
import java.util.UUID;

public record PartListingResponse(
        UUID publicId, UUID sellerUserPublicId, String name, String description,
        String vehicleMake, String vehicleModel, Integer vehicleYear,
        String condition, String category, long amountMinor, String currency,
        String businessStatus, String deliveryStatus, String storageStatus,
        long viewCount, Instant createdAt, Instant updatedAt, long version) {
}
