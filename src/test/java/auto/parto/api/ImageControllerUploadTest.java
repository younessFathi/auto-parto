package auto.parto.api;

import auto.parto.persistence.FileRecord;
import auto.parto.persistence.FileRepository;
import auto.parto.security.CanonicalPrincipal;
import auto.parto.service.iservice.IFileManagerService;
import auto.parto.storage.FileStorage;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ImageControllerUploadTest {

    private final IFileManagerService service = mock(IFileManagerService.class);
    private final ImageController controller = new ImageController(service);
    private final UUID owner = UUID.randomUUID();
    private final UUID request = UUID.randomUUID();
    private final FilePart image = mock(FilePart.class);

    @Test
    void uploadRequestImageUsesAuthenticatedBuyerIdentity() {
        Authentication authentication = authentication(owner, "ROLE_BUYER");
        when(service.saveForRequest(image, owner, request)).thenReturn(Mono.just("file-1"));

        String result = controller.uploadRequestImage(request, image, authentication).block();

        assertThat(result).isEqualTo("file-1");
        verify(service).saveForRequest(image, owner, request);
    }

    @Test
    void uploadRequestImageRejectsNonBuyer() {
        Authentication authentication = authentication(owner, "ROLE_SELLER");

        assertThatThrownBy(() -> controller.uploadRequestImage(request, image, authentication))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verifyNoInteractions(service);
    }

    @Test
    void uploadListingImageUsesAuthenticatedSellerIdentity() {
        Authentication authentication = authentication(owner, "ROLE_SELLER");
        when(service.saveForListing(image, owner, request)).thenReturn(Mono.just("file-2"));

        String result = controller.uploadListingImage(request, image, authentication).block();

        assertThat(result).isEqualTo("file-2");
        verify(service).saveForListing(image, owner, request);
    }

    @Test
    void canonicalFileReadStreamsActiveObjectFromStorage() {
        FileRepository repository = mock(FileRepository.class);
        FileStorage storage = mock(FileStorage.class);
        UUID fileId = UUID.randomUUID();
        when(repository.findByPublicId(fileId)).thenReturn(Mono.just(new FileRecord(fileId, owner, request, null,
                "requests/object.jpg", "object.jpg", "image/jpeg", 7, "checksum", "ACTIVE",
                Instant.now(), Instant.now())));
        when(storage.read("requests/object.jpg")).thenReturn(Flux.just(
                new DefaultDataBufferFactory().wrap("payload".getBytes())));

        var response = new ImageController(service, repository, storage).getFile(fileId).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(storage).read("requests/object.jpg");
    }

    @Test
    void canonicalFileReadReturnsNotFoundForMissingMetadata() {
        FileRepository repository = mock(FileRepository.class);
        FileStorage storage = mock(FileStorage.class);
        UUID fileId = UUID.randomUUID();
        when(repository.findByPublicId(fileId)).thenReturn(Mono.empty());

        var response = new ImageController(service, repository, storage).getFile(fileId).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        verifyNoInteractions(storage);
    }

    private static Authentication authentication(UUID owner, String role) {
        CanonicalPrincipal principal = new CanonicalPrincipal(owner.toString(), "user@example.test", role);
        return new UsernamePasswordAuthenticationToken(principal, "token", principal.getAuthorities());
    }
}
