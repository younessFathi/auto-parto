package auto.parto.persistence;

import java.util.UUID;

public record BuyerProfileRecord(
        UUID publicId,
        UUID userPublicId,
        String firstName,
        String lastName,
        String phoneNumber,
        String pictureUrl) {
}
