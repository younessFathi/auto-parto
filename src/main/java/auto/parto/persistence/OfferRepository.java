package auto.parto.persistence;

import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

public interface OfferRepository {

    Mono<OfferRecord> create(UUID buyerUserPublicId, UUID sellerUserPublicId,
                              UUID partRequestPublicId, UUID partListingPublicId,
                              long amountMinor, String currency, boolean negotiable,
                              long deliveryAmountMinor, String deliveryMode);

    Mono<OfferRecord> findByPublicId(UUID publicId);

    Mono<OfferRecord> updateStatus(UUID publicId, UUID actorUserPublicId, String status,
                                   long expectedVersion, Instant updatedAt);
}
