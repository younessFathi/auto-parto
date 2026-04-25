package auto.parto.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DockerComposeConfigurationTest {

    @Test
    void dockerComposeIncludesSpringBootMongoAndRedisWiring() throws IOException {
        String compose = Files.readString(Path.of("docker-compose.yml"));

        assertThat(compose).contains("springboot-app:");
        assertThat(compose).contains("SPRING_DATA_MONGODB_URI: ${URI}");
        assertThat(compose).contains("SPRING_DATA_REDIS_HOST: redis");
        assertThat(compose).contains("REDIS_PASSWORD: ${REDIS_PASSWORD}");
        assertThat(compose).contains("depends_on:");
        assertThat(compose).contains("mongo:");
        assertThat(compose).contains("redis:");
    }
}
