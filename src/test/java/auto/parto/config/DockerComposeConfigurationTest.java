package auto.parto.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class DockerComposeConfigurationTest {

    @Test
    void dockerComposeIncludesSpringBootMongoAndRedisWiring() throws IOException {
        String compose = Files.readString(Path.of("docker-compose.yml"));

        assertThat(compose).contains("springboot-app:");
        assertThat(compose).contains("SPRING_DATA_MONGODB_URI: ${MONGODB_URI:-mongodb://root:root@mongo:27017/auto-parto?authSource=admin}");
        assertThat(compose).contains("SPRING_DATA_REDIS_HOST: redis");
        assertThat(compose).contains("REDIS_PASSWORD: ${REDIS_PASSWORD:-password}");
        assertThat(compose).contains("JWT_SECRET: ${JWT_SECRET:-dev-jwt-secret-key-that-is-256-bits-long!}");
        assertThat(compose).contains("command: redis-server --appendonly yes --requirepass ${REDIS_PASSWORD:-password}");
        assertThat(compose).contains("test: redis-cli -a '${REDIS_PASSWORD:-password}' ping");
        assertThat(compose).contains("depends_on:");
        assertThat(compose).contains("mongo:");
        assertThat(compose).contains("redis:");
    }

    @Test
    void postgresProfileDefinesIndependentApplicationAndAuditResources() throws IOException {
        String compose = Files.readString(Path.of("docker-compose.yml"));

        assertThat(compose).contains("app-postgresql:");
        String auditCompose = Files.readString(Path.of("docker-compose.audit.yml"));
        assertThat(auditCompose).contains("audit-postgresql:");
        assertThat(compose).contains("APP_POSTGRES_MIGRATION_PASSWORD");
        assertThat(compose).contains("app-postgresql-migrate:");
        assertThat(compose).contains("flyway/flyway:10.20.1");
        assertThat(compose).contains("FLYWAY_LOCATIONS: filesystem:/flyway/sql");
        assertThat(compose).contains("FLYWAY_USER: ${APP_POSTGRES_MIGRATION_USER");
        assertThat(compose).contains("APP_POSTGRES_RUNTIME_PASSWORD");
        assertThat(compose).contains("APP_POSTGRES_BOOTSTRAP_PASSWORD");
        assertThat(compose).contains("./deploy/postgres/init:/docker-entrypoint-initdb.d:ro");
        assertThat(compose).contains("./src/main/resources/db/migration:/flyway/sql:ro");
        assertThat(compose).contains("restart: \"no\"");
        assertThat(compose).contains("app-postgres-data:");
        assertThat(compose).contains("profiles: [\"postgres\"]");
        assertThat(auditCompose).contains("AUDIT_POSTGRES_PASSWORD");
        assertThat(auditCompose).contains("profiles: [\"audit\"]");
    }

    @Test
    void postgresOnlyComposeConfigDoesNotRequireAuditSecret() throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder(
                "docker", "compose", "--profile", "postgres", "config", "--quiet");
        processBuilder.directory(Path.of(".").toFile());
        processBuilder.redirectErrorStream(true);
        Map<String, String> environment = processBuilder.environment();
        environment.remove("AUDIT_POSTGRES_PASSWORD");
        environment.put("APP_POSTGRES_BOOTSTRAP_PASSWORD", "bootstrap-test-value");
        environment.put("APP_POSTGRES_RUNTIME_PASSWORD", "runtime-test-value");
        environment.put("APP_POSTGRES_MIGRATION_USER", "schema_migrator");
        environment.put("APP_POSTGRES_MIGRATION_PASSWORD", "migration-test-value");

        Process process;
        try {
            process = processBuilder.start();
        } catch (IOException unavailable) {
            assumeTrue(false, "Docker Compose is not available");
            return;
        }
        String output = new String(process.getInputStream().readAllBytes());
        assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).withFailMessage(output).isZero();
    }
}
