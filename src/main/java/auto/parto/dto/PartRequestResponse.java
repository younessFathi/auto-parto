package auto.parto.dto;

import java.time.Instant;
import java.util.UUID;

public record PartRequestResponse(
        UUID publicId,
        UUID buyerUserPublicId,
        String name,
        String description,
        String condition,
        String category,
        String vehicleMake,
        String vehicleModel,
        Integer vehicleYear,
        String status,
        Instant createdAt,
        Instant updatedAt,
        long version) {
}
