package auto.parto.persistence;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.List;
import java.time.Instant;

public interface FileRepository {

    Mono<FileRecord> createForRequest(UUID ownerUserPublicId, UUID requestPublicId, String objectKey,
                                      String originalFilename, String contentType, long byteSize, String checksum);

    default Mono<FileRecord> createForRequest(UUID ownerUserPublicId, UUID requestPublicId, String objectKey,
                                              String originalFilename, String contentType, long byteSize,
                                              String checksum, long activeFileQuota) {
        return createForRequest(ownerUserPublicId, requestPublicId, objectKey, originalFilename, contentType,
                byteSize, checksum);
    }

    Mono<FileRecord> createForListing(UUID ownerUserPublicId, UUID listingPublicId, String objectKey,
                                      String originalFilename, String contentType, long byteSize, String checksum);

    default Mono<FileRecord> createForListing(UUID ownerUserPublicId, UUID listingPublicId, String objectKey,
                                              String originalFilename, String contentType, long byteSize,
                                              String checksum, long activeFileQuota) {
        return createForListing(ownerUserPublicId, listingPublicId, objectKey, originalFilename, contentType,
                byteSize, checksum);
    }

    Flux<FileRecord> findByRequest(UUID requestPublicId, int limit);

    Flux<FileRecord> findByListing(UUID listingPublicId, int limit);

    Flux<FileRecord> findByPublicIds(List<UUID> filePublicIds, int limit);

    Flux<FileRecord> findActive(int limit);

    Mono<Long> countActive();

    default Flux<FileRecord> findStalePending(Instant createdBefore, int limit) {
        return Flux.empty();
    }

    default Mono<Long> countActiveByOwner(UUID ownerUserPublicId) {
        return Mono.empty();
    }

    Mono<FileRecord> findByPublicId(UUID filePublicId);

    Mono<FileRecord> activate(UUID filePublicId, UUID ownerUserPublicId);

    Mono<Boolean> tombstone(UUID filePublicId, UUID ownerUserPublicId);

    default Mono<Boolean> tombstonePending(UUID filePublicId, UUID ownerUserPublicId) {
        return tombstone(filePublicId, ownerUserPublicId);
    }
}
