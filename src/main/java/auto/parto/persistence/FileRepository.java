package auto.parto.persistence;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.List;

public interface FileRepository {

    Mono<FileRecord> createForRequest(UUID ownerUserPublicId, UUID requestPublicId, String objectKey,
                                      String originalFilename, String contentType, long byteSize, String checksum);

    Mono<FileRecord> createForListing(UUID ownerUserPublicId, UUID listingPublicId, String objectKey,
                                      String originalFilename, String contentType, long byteSize, String checksum);

    Flux<FileRecord> findByRequest(UUID requestPublicId, int limit);

    Flux<FileRecord> findByListing(UUID listingPublicId, int limit);

    Flux<FileRecord> findByPublicIds(List<UUID> filePublicIds, int limit);

    Flux<FileRecord> findActive(int limit);

    Mono<Long> countActive();

    Mono<FileRecord> findByPublicId(UUID filePublicId);

    Mono<FileRecord> activate(UUID filePublicId, UUID ownerUserPublicId);

    Mono<Boolean> tombstone(UUID filePublicId, UUID ownerUserPublicId);
}
