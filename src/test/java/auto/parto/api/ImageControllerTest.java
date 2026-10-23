package auto.parto.api;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@WebFluxTest(controllers = ImageController.class)
@Import(TestConfig.class)
@TestPropertySource(properties = "auto.parto.persistence.postgresql.files.enabled=false")
class ImageControllerTest {

    private static Path tempDir;

    @Autowired
    private WebTestClient webTestClient;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) throws IOException {
        tempDir = Files.createTempDirectory("img-test");
        registry.add("auto.parto.images.uploads", tempDir::toString);
    }

    @Test
    @WithMockUser
    void listImages_returnsImageFiles() throws IOException {
        Path folder = tempDir.resolve("test-folder");
        Files.createDirectories(folder);
        Files.createFile(folder.resolve("image1.jpg"));
        Files.createFile(folder.resolve("image2.png"));

        webTestClient.get()
                .uri("/images/test-folder")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @WithMockUser
    void listImages_returnsSingleImage_withFlagOne() throws IOException {
        Path folder = tempDir.resolve("single-folder");
        Files.createDirectories(folder);
        Files.createFile(folder.resolve("only.jpg"));

        webTestClient.get()
                .uri("/images/single-folder?flag=ONE")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @WithMockUser
    void listImages_returns400_whenInvalidFolderName() {
        webTestClient.get()
                .uri("/images/../invalid")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @WithMockUser
    void listImages_returns500_whenFolderNotFound() {
        webTestClient.get()
                .uri("/images/nonexistent")
                .exchange()
                .expectStatus().is5xxServerError();
    }

    @Test
    void listImages_returns401_whenNotAuthenticated() {
        webTestClient.get()
                .uri("/images/test-folder")
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
