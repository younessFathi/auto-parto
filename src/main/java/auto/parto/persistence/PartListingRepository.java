package auto.parto.persistence;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

public interface PartListingRepository {

    Mono<PartListingRecord> create(UUID sellerUserPublicId, String name, String description,
                                    String condition, String category, long amountMinor, String currency);

    Mono<PartListingRecord> findByPublicId(UUID publicId);

    Flux<PartListingRecord> findBySellerPublicId(UUID sellerUserPublicId, int page, int size);

    Mono<Long> countBySellerPublicId(UUID sellerUserPublicId);

    Mono<PartListingRecord> updateStatus(UUID publicId, UUID sellerUserPublicId, String status,
                                         long expectedVersion, Instant updatedAt);
}
