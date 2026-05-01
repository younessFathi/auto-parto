package auto.parto.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigurationPlaceholdersTest {

    @Test
    void applicationConfigUsesEnvironmentPlaceholdersForSecrets() throws IOException {
        String applicationConfig = Files.readString(Path.of("src/main/resources/application.yml"));

        assertThat(applicationConfig).contains("client-id: ${CLIENT_ID}");
        assertThat(applicationConfig).contains("client-secret: ${CLIENT_SECRET}");
        assertThat(applicationConfig).contains("secretKey: ${JWT_SECRET}");
        assertThat(applicationConfig).contains("api-key: ${RESEND_API_KEY}");

        assertThat(applicationConfig).doesNotContain("GOOGLE_CLIENT_SECRET_PREFIX-");
        assertThat(applicationConfig).doesNotContain("AC[REDACTED]");
        assertThat(applicationConfig).doesNotContain("re_[REDACTED]");
    }
}
