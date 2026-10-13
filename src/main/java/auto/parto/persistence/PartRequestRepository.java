package auto.parto.persistence;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

public interface PartRequestRepository {

    Mono<PartRequestRecord> create(UUID buyerUserPublicId, String name, String description,
                                    String condition, String category);

    Mono<PartRequestRecord> findByPublicId(UUID publicId);

    Flux<PartRequestRecord> findByBuyerPublicId(UUID buyerUserPublicId, int page, int size);

    Mono<Long> countByBuyerPublicId(UUID buyerUserPublicId);

    Mono<PartRequestRecord> updateStatus(UUID publicId, UUID buyerUserPublicId, String status,
                                         long expectedVersion, Instant updatedAt);
}
