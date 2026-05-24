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
        assertThat(compose).contains("SPRING_DATA_MONGODB_URI: ${URI:-mongodb://root:root@mongo:27017/auto-parto?authSource=admin}");
        assertThat(compose).contains("SPRING_DATA_REDIS_HOST: redis");
        assertThat(compose).contains("REDIS_PASSWORD: ${REDIS_PASSWORD:-password}");
        assertThat(compose).contains("JWT_SECRET: ${JWT_SECRET:-dev-jwt-secret-key-that-is-256-bits-long!}");
        assertThat(compose).contains("command: redis-server --appendonly yes --requirepass ${REDIS_PASSWORD:-password}");
        assertThat(compose).contains("test: redis-cli -a '${REDIS_PASSWORD:-password}' ping");
        assertThat(compose).contains("depends_on:");
        assertThat(compose).contains("mongo:");
        assertThat(compose).contains("redis:");
    }
}
