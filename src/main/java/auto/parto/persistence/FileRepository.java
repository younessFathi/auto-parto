package auto.parto.persistence;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface FileRepository {

    Mono<FileRecord> createForRequest(UUID ownerUserPublicId, UUID requestPublicId, String objectKey,
                                      String originalFilename, String contentType, long byteSize, String checksum);

    Mono<FileRecord> createForListing(UUID ownerUserPublicId, UUID listingPublicId, String objectKey,
                                      String originalFilename, String contentType, long byteSize, String checksum);

    Flux<FileRecord> findByRequest(UUID requestPublicId, int limit);

    Flux<FileRecord> findByListing(UUID listingPublicId, int limit);

    Mono<Boolean> tombstone(UUID filePublicId, UUID ownerUserPublicId);
}
