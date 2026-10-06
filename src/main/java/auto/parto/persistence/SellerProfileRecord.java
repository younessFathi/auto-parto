package auto.parto.persistence;

import java.util.UUID;

public record SellerProfileRecord(
        UUID publicId,
        UUID userPublicId,
        String firstName,
        String lastName,
        String phoneNumber,
        String partType,
        String description) {
}
