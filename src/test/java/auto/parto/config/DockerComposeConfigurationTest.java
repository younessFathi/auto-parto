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
    void dockerComposeIncludesSpringBootPostgresqlAndRedisWiring() throws IOException {
        String compose = Files.readString(Path.of("docker-compose.yml"));

        assertThat(compose).contains("springboot-postgresql-app:")
                .doesNotContain("\n  springboot-app:\n");
        assertThat(compose).contains("expose:\n      - \"8080\"")
                .doesNotContain("container_name: springboot-app")
                .contains("stop_grace_period: 20s")
                .contains("read_only: true")
                .contains("pids_limit: 256")
                .contains("no-new-privileges:true")
                .contains("mem_limit: 1g")
                .contains("cpus: \"1.0\"");
        assertThat(compose).contains("SPRING_R2DBC_URL: r2dbc:postgresql://app-postgresql:5432/${APP_POSTGRES_DB:-auto_parto}");
        assertThat(compose).contains("SPRING_DATA_REDIS_HOST: redis");
        assertThat(compose).contains("REDIS_PASSWORD: ${REDIS_PASSWORD:?set REDIS_PASSWORD for the postgres profile}");
        assertThat(compose).contains("JWT_SECRET: ${JWT_SECRET:?set JWT_SECRET for the postgres profile}");
        assertThat(compose).contains("command: redis-server --appendonly yes --requirepass ${REDIS_PASSWORD:?set REDIS_PASSWORD for the postgres profile}");
        assertThat(compose).contains("test: redis-cli -a '${REDIS_PASSWORD:?set REDIS_PASSWORD for the postgres profile}' ping");
        assertThat(compose).contains("depends_on:");
        assertThat(compose).doesNotContain("MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS: always");
        assertThat(compose).doesNotContain("mongo:").doesNotContain("MONGODB");
        assertThat(compose).contains("redis:");
    }

    @Test
    void postgresProfileDefinesIndependentApplicationAndAuditResources() throws IOException {
        String compose = Files.readString(Path.of("docker-compose.yml"));

        assertThat(compose).contains("springboot-postgresql-app:");
        assertThat(compose).doesNotContain("container_name: springboot-postgresql-app")
                .contains("stop_grace_period: 20s");
        assertThat(compose).doesNotContain("MongoReactiveAutoConfiguration");
        assertThat(compose).contains("APP_POSTGRES_CUTOVER_ENABLED: \"true\"");
        assertThat(compose).doesNotContain("APP_POSTGRES_MONGO_DISABLED");
        assertThat(compose).contains("condition: service_completed_successfully");
        assertThat(compose).contains("app-postgresql:");
        String auditCompose = Files.readString(Path.of("docker-compose.audit.yml"));
        assertThat(auditCompose).contains("audit-postgresql:");
        assertThat(compose).contains("APP_POSTGRES_MIGRATION_PASSWORD");
        assertThat(compose).contains("app-postgresql-migrate:");
        assertThat(compose).contains("app-postgresql-fixtures:");
        assertThat(compose).contains("flyway/flyway:10.20.1");
        assertThat(compose).contains("FLYWAY_LOCATIONS: filesystem:/flyway/sql");
        assertThat(compose).contains("FLYWAY_USER: ${APP_POSTGRES_MIGRATION_USER");
        assertThat(compose).contains("APP_POSTGRES_RUNTIME_PASSWORD");
        assertThat(compose).contains("APP_POSTGRES_BOOTSTRAP_PASSWORD");
        assertThat(compose).contains("./deploy/postgres/init:/docker-entrypoint-initdb.d:ro");
        assertThat(compose).contains("./src/main/resources/db/migration:/flyway/sql:ro");
        assertThat(compose).contains("./deploy/postgres/fixtures:/fixtures:ro");
        assertThat(compose).contains("PGUSER: ${APP_POSTGRES_RUNTIME_USER:-auto_parto_runtime}");
        assertThat(compose).contains("condition: service_completed_successfully");
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
        environment.put("REDIS_PASSWORD", "compose-test-redis");
        environment.put("JWT_SECRET", "compose-test-jwt");

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

    @Test
    void localOverridePublishesPortsWithoutPreventingReplicaScaling() throws IOException {
        String localCompose = Files.readString(Path.of("docker-compose.local.yml"));

        assertThat(localCompose).contains("127.0.0.1:8081:8080")
                .doesNotContain("127.0.0.1:8080:8080");
    }
}
