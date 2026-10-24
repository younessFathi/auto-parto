package auto.parto.api;

import auto.parto.persistence.FileRepository;
import auto.parto.storage.FileStorage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.UUID;

@WebFluxTest(controllers = ImageController.class)
@Import(TestConfig.class)
class ImageControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private FileRepository fileRepository;

    @MockitoBean
    private FileStorage fileStorage;

    @Test
    @WithMockUser
    void legacyFolderRouteIsRetired() {
        webTestClient.get()
                .uri("/images/test-folder")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void canonicalFileRouteRequiresAuthentication() {
        webTestClient.get()
                .uri("/api/v1/files/" + UUID.randomUUID())
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
