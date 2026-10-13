package auto.parto.dto;

import java.time.Instant;
import java.util.UUID;

public record OfferResponse(
        UUID publicId,
        UUID partRequestPublicId,
        UUID partListingPublicId,
        UUID buyerUserPublicId,
        UUID sellerUserPublicId,
        long amountMinor,
        String currency,
        String status,
        boolean negotiable,
        long deliveryAmountMinor,
        String deliveryMode,
        Instant createdAt,
        Instant updatedAt) {
}
