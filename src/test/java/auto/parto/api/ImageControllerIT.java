package auto.parto.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@WebFluxTest(controllers = ImageController.class)
@ActiveProfiles("test")
public class ImageControllerIT {
    @Autowired
    private WebTestClient webTestClient;


    @Test
    @WithMockUser
    void testListImages_Success() throws IOException {
        webTestClient.mutateWith(SecurityMockServerConfigurers.csrf())
                .get().uri("/images/test-folder")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Resource.class)
                .hasSize(1);
    }

    @Test
    @WithMockUser
    void testListImages_InvalidFolder() {
        webTestClient.mutateWith(SecurityMockServerConfigurers.csrf())
                .get().uri("/images/invalid-folder")
                .exchange()
                .expectStatus().is5xxServerError();
    }

    @Test
    @WithMockUser
    void testListImages_WithFlagOne() {
        webTestClient.mutateWith(SecurityMockServerConfigurers.csrf())
                .get().uri(uriBuilder ->
                        uriBuilder.path("/images/test-folder")
                                .queryParam("flag", "ONE")
                                .build())
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Resource.class)
                .hasSize(1);
    }
}
