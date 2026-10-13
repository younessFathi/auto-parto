package auto.parto.service.iservice;

import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface IFileManagerService {
    Mono<String> save(FilePart image , String pieceId);

    Mono<String> saveForRequest(FilePart image, UUID ownerUserPublicId, UUID requestPublicId);

    Mono<String> saveForListing(FilePart image, UUID ownerUserPublicId, UUID listingPublicId);
}
