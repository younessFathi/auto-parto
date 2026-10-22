package auto.parto.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationSecretsConfigTest {

    @Test
    void doesNotHardcodeDatabaseAndRedisSecretsInApplicationYaml() throws IOException {
        String content = Files.readString(Path.of("src/main/resources/application.yml"));

        assertThat(content).contains("uri: ${MONGODB_URI:mongodb://localhost:27017/auto-parto}");
        assertThat(content).doesNotContain("uri: ${URI}");
        assertThat(content).contains("password: ${REDIS_PASSWORD:-}");
        assertThat(content).doesNotContain("mongodb://root:root@localhost");
        assertThat(content).doesNotContain("password: root");
    }
}
