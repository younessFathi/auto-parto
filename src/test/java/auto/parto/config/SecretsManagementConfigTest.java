package auto.parto.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SecretsManagementConfigTest {

    @Test
    void gitignoreContainsEnv() throws IOException {
        String gitignore = Files.readString(Path.of(".gitignore"));
        assertThat(gitignore).contains(".env");
    }

    @Test
    void envExampleExists() {
        assertThat(Path.of(".env.example")).exists().isRegularFile();
    }

    @Test
    void envExampleContainsAllRequiredEnvironmentVariables() throws IOException {
        String content = Files.readString(Path.of(".env.example"));

        assertThat(content)
                .contains("SPRING_PROFILES_ACTIVE")
                .contains("URI")
                .contains("REDIS_HOST")
                .contains("REDIS_PORT")
                .contains("REDIS_PASSWORD")
                .contains("JWT_SECRET")
                .contains("CLIENT_ID")
                .contains("CLIENT_SECRET")
                .contains("FIREBASE_ENABLED")
                .contains("FIREBASE_CREDENTIALS_JSON")
                .contains("RESEND_API_KEY");
    }

    @Test
    void envExampleUsesPlaceholderValuesNotRealSecrets() throws IOException {
        String content = Files.readString(Path.of(".env.example"));

        List.of(
                "your-google-oauth-client-secret",
                "your-resend-api-key",
                "your-256-bit-base64-encoded-secret"
        ).forEach(placeholder ->
                assertThat(content).contains(placeholder)
        );

        assertThat(content)
                .doesNotContain("GOCSPX-")
                .doesNotContain("AC617874")
                .doesNotContain("re_Hi3")
                .doesNotContain("763801544769");
    }

    @Test
    void envExampleHasNoHardcodedRealValues() throws IOException {
        String content = Files.readString(Path.of(".env.example"));

        assertThat(content)
                .doesNotContain("root:root")
                .doesNotContain("mongodb://root:root@")
                .doesNotContain("password: root");
    }
}
