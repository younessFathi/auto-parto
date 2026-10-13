package auto.parto.api;

import auto.parto.security.CanonicalPrincipal;
import auto.parto.service.iservice.IFileManagerService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;

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

    private static Authentication authentication(UUID owner, String role) {
        CanonicalPrincipal principal = new CanonicalPrincipal(owner.toString(), "user@example.test", role);
        return new UsernamePasswordAuthenticationToken(principal, "token", principal.getAuthorities());
    }
}
