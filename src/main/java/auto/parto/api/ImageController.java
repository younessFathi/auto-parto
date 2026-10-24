package auto.parto.api;

import auto.parto.persistence.FileRecord;
import auto.parto.persistence.FileRepository;
import auto.parto.security.CanonicalPrincipal;
import auto.parto.security.CanonicalSecurity;
import auto.parto.service.iservice.IFileManagerService;
import auto.parto.storage.FileStorage;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Canonical file metadata and upload API. Legacy folder browsing is retired. */
@RestController
@RequestMapping("/api/v1/files")
public class ImageController {
    private final IFileManagerService fileManagerService;
    private final FileRepository fileRepository;
    private final FileStorage fileStorage;

    public ImageController() {
        this((IFileManagerService) null, (FileRepository) null, (FileStorage) null);
    }

    public ImageController(IFileManagerService fileManagerService) {
        this(fileManagerService, null, null);
    }

    public ImageController(IFileManagerService fileManagerService, FileRepository fileRepository,
                           FileStorage fileStorage) {
        this.fileManagerService = fileManagerService;
        this.fileRepository = fileRepository;
        this.fileStorage = fileStorage;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ImageController(ObjectProvider<IFileManagerService> provider,
                           ObjectProvider<FileRepository> fileRepositoryProvider,
                           ObjectProvider<FileStorage> fileStorageProvider) {
        this(provider.getIfAvailable(), fileRepositoryProvider.getIfAvailable(), fileStorageProvider.getIfAvailable());
    }

    @PostMapping(value = "/requests/{requestId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('BUYER')")
    public Mono<String> uploadRequestImage(@PathVariable UUID requestId,
                                            @RequestPart("image") FilePart image,
                                            Authentication authentication) {
        CanonicalPrincipal principal = CanonicalSecurity.requireRole(authentication, "ROLE_BUYER");
        UUID ownerId = UUID.fromString(principal.getId());
        if (fileManagerService == null) {
            return Mono.error(new IllegalStateException("File upload service is unavailable"));
        }
        return fileManagerService.saveForRequest(image, ownerId, requestId);
    }

    @PostMapping(value = "/listings/{listingId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('SELLER')")
    public Mono<String> uploadListingImage(@PathVariable UUID listingId,
                                            @RequestPart("image") FilePart image,
                                            Authentication authentication) {
        CanonicalPrincipal principal = CanonicalSecurity.requireRole(authentication, "ROLE_SELLER");
        UUID ownerId = UUID.fromString(principal.getId());
        if (fileManagerService == null) {
            return Mono.error(new IllegalStateException("File upload service is unavailable"));
        }
        return fileManagerService.saveForListing(image, ownerId, listingId);
    }

    @GetMapping("/{fileId}")
    @PreAuthorize("isAuthenticated()")
    public Mono<ResponseEntity<Flux<DataBuffer>>> getFile(@PathVariable UUID fileId) {
        if (fileRepository == null || fileStorage == null) {
            return Mono.just(ResponseEntity.status(503).build());
        }
        return fileRepository.findByPublicId(fileId)
                .filter(record -> "ACTIVE".equals(record.status()))
                .map(this::toResponse)
                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
    }

    private ResponseEntity<Flux<DataBuffer>> toResponse(FileRecord record) {
        MediaType contentType;
        try {
            contentType = MediaType.parseMediaType(record.contentType());
        } catch (IllegalArgumentException ignored) {
            contentType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok().contentType(contentType).body(fileStorage.read(record.objectKey()));
    }
}
