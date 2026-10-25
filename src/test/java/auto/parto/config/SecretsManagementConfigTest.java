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
                .contains("APP_POSTGRES_R2DBC_URL")
                .contains("REDIS_HOST")
                .contains("REDIS_PORT")
                .contains("REDIS_PASSWORD")
                .contains("JWT_SECRET")
                .contains("FIREBASE_ENABLED")
                .contains("FIREBASE_CREDENTIALS_JSON")
                .contains("RESEND_API_KEY");
    }

    @Test
    void envExampleUsesPlaceholderValuesNotRealSecrets() throws IOException {
        String content = Files.readString(Path.of(".env.example"));

        List.of(
                "your-resend-api-key",
                "your-256-bit-base64-encoded-secret"
        ).forEach(placeholder ->
                assertThat(content).contains(placeholder)
        );

        assertThat(content)
                .doesNotContain("GOOGLE_CLIENT_SECRET_PREFIX-")
                .doesNotContain("AC[REDACTED]")
                .doesNotContain("re_[REDACTED]")
                .doesNotContain("GOOGLE_PROJECT_NUMBER");
    }

    @Test
    void envExampleHasNoHardcodedRealValues() throws IOException {
        String content = Files.readString(Path.of(".env.example"));

        assertThat(content)
                .doesNotContain("root:root")
                .doesNotContain("password: root");
    }

    @Test
    void productionProfileUsesRequiredExternalPlaceholders() throws IOException {
        String content = Files.readString(Path.of("src/main/resources/application-prod.yml"));

        assertThat(content)
                .contains("url: ${APP_POSTGRES_R2DBC_URL}")
                .contains("password: ${APP_POSTGRES_RUNTIME_PASSWORD}")
                .contains("password: ${REDIS_PASSWORD}")
                .contains("provider: ${APP_SECRET_PROVIDER}")
                .contains("address: ${OPENBAO_ADDR}")
                .contains("provider: ${APP_FILE_STORAGE_PROVIDER}")
                .doesNotContain("localhost")
                .doesNotContain("root");
    }
}
