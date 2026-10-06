package auto.parto.dto;

import java.util.UUID;

public record SellerProfileResponse(
        UUID publicId,
        UUID userPublicId,
        String firstName,
        String lastName,
        String phoneNumber,
        String partType,
        String description) {
}
