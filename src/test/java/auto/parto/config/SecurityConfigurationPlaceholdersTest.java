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

        assertThat(applicationConfig).contains("provider: otp");
        assertThat(applicationConfig).contains("enabled: false");
        assertThat(applicationConfig).doesNotContain("oauth2");
        assertThat(applicationConfig).contains("secretKey: ${JWT_SECRET}");
        assertThat(applicationConfig).contains("api-key: ${RESEND_API_KEY}");

        assertThat(applicationConfig).doesNotContain("GOCSPX-");
        assertThat(applicationConfig).doesNotContain("AC617874");
        assertThat(applicationConfig).doesNotContain("re_Hi3");
    }

    @Test
    void devProfileAlsoUsesEnvironmentPlaceholdersForSecrets() throws IOException {
        String devConfig = Files.readString(Path.of("src/main/resources/application-dev.yml"));

        assertThat(devConfig).contains("provider: otp");
        assertThat(devConfig).contains("secretKey: ${JWT_SECRET}");
        assertThat(devConfig).doesNotContain("oauth2");
        assertThat(Path.of("src/main/java/auto/parto/api/ClerkReactiveController.java")).doesNotExist();
        assertThat(Path.of("src/main/java/auto/parto/config/filter/ClerkJwtAuthenticationFilter.java")).doesNotExist();
        assertThat(devConfig).doesNotContain("H1lq8Mx5WjD3+KPxBlj+F+Qo5rFkNc1uWy2FpiGpWXM=");
    }
}
