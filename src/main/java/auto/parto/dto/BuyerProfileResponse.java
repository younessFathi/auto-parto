package auto.parto.dto;

import java.util.UUID;

public record BuyerProfileResponse(
        UUID publicId,
        UUID userPublicId,
        String firstName,
        String lastName,
        String phoneNumber,
        String pictureUrl) {
}
