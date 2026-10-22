package auto.parto.config;

import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class DeploymentComposeIT {

    private static final Path PROJECT_ROOT = Path.of(".").toAbsolutePath().normalize();
    private static final Duration COMMAND_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(120);

    @Test
    void composeApplicationUsesDeclaredDeploymentConstraintsAndReachesLiveness() throws Exception {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is not available");

        String projectName = "auto-parto-ap0020-" + UUID.randomUUID().toString().replace('-', '_');
        String imageName = "auto-parto-compose-smoke:" + UUID.randomUUID().toString().replace('-', '_');
        Path overrideFile = Files.createTempFile("auto-parto-compose-", ".yml");
        List<String> compose = composeCommand(projectName, overrideFile);

        Files.writeString(overrideFile, composeOverride(imageName));
        try {
            CommandResult rendered = run(compose, "config");
            if (rendered.exitCode() != 0) {
                throw new AssertionError("Compose config failed: " + rendered.stdout());
            }
            assertThat(rendered.stdout()).as(rendered.stdout()).contains("read_only: true")
                    .contains("pids_limit: 256")
                    .contains("mem_limit: \"1073741824\"")
                    .contains("cpus: 1")
                    .contains("- /tmp");

            CommandResult started = run(compose, "up", "-d", "--build", "--quiet-pull", "springboot-app");
            assertThat(started.exitCode()).as(started.stdout()).isZero();

            waitForLiveness(compose);
            String containerId = run(compose, "ps", "-q", "springboot-app").stdout().trim();
            assertThat(containerId).isNotBlank();

            String inspected = run(List.of("docker", "inspect", "--format", "{{json .}}", containerId)).stdout();
            assertThat(inspected).contains("\"User\":\"spring:spring\"")
                    .contains("\"ReadonlyRootfs\":true")
                    .contains("\"Memory\":1073741824")
                    .contains("\"NanoCpus\":1000000000")
                    .contains("\"PidsLimit\":256")
                    .contains("\"/tmp\"");
        } finally {
            run(compose, "down", "--volumes", "--remove-orphans");
            run(List.of("docker", "image", "rm", "--force", imageName));
            Files.deleteIfExists(overrideFile);
        }
    }

    private static void waitForLiveness(List<String> compose) throws Exception {
        Instant deadline = Instant.now().plus(STARTUP_TIMEOUT);
        CommandResult last = null;
        while (Instant.now().isBefore(deadline)) {
            last = run(compose, "exec", "-T", "springboot-app", "wget", "-qO-",
                    "http://localhost:8080/actuator/health/liveness");
            if (last.exitCode() == 0 && last.stdout().contains("\"status\":\"UP\"")) {
                return;
            }
            Thread.sleep(1000L);
        }
        assertThat(last).as("application liveness response").isNotNull();
        CommandResult logs = run(compose, "logs", "--no-color", "--tail", "100", "springboot-app");
        throw new AssertionError("Application did not reach liveness: " + last.stdout() + "\n" + logs.stdout());
    }

    private static List<String> composeCommand(String projectName, Path overrideFile) {
        return List.of("docker", "compose", "--project-name", projectName,
                "--file", PROJECT_ROOT.resolve("docker-compose.yml").toString(),
                "--file", overrideFile.toString());
    }

    private static String composeOverride(String imageName) {
        return """
                services:
                  springboot-app:
                    image: %s
                    environment:
                      SPRING_PROFILES_ACTIVE: local
                      SPRING_DATA_MONGODB_URI: mongodb://root:root@mongo:27017/auto-parto?authSource=admin
                      SPRING_DATA_REDIS_HOST: redis
                      SPRING_DATA_REDIS_PORT: "6379"
                      REDIS_PASSWORD: password
                      JWT_SECRET: MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=
                      OTP_PEPPER: compose-smoke-test-pepper
                      FIREBASE_ENABLED: "false"
                      APP_FIREBASE_COMPONENTS_ENABLED: "false"
                      RESEND_API_KEY: compose-smoke-test-disabled
                      APP_FILE_STORAGE_PROVIDER: local
                      APP_FILE_STORAGE_LOCAL_ROOT: /tmp/uploads
                      APP_AUDIT_OUTBOX_ENABLED: "false"
                  mongo:
                    container_name: !reset null
                    ports: !reset []
                  redis:
                    container_name: !reset null
                    ports: !reset []
                """.formatted(imageName);
    }

    private static CommandResult run(List<String> command, String... arguments) throws IOException, InterruptedException {
        var fullCommand = new java.util.ArrayList<>(command);
        fullCommand.addAll(List.of(arguments));
        return run(fullCommand);
    }

    private static CommandResult run(List<String> command) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(command)
                .directory(PROJECT_ROOT.toFile())
                .redirectErrorStream(true);
        processBuilder.environment().putAll(java.util.Map.ofEntries(
                java.util.Map.entry("REDIS_PASSWORD", "password"),
                java.util.Map.entry("APP_POSTGRES_BOOTSTRAP_PASSWORD", "compose-smoke-test-bootstrap"),
                java.util.Map.entry("APP_POSTGRES_RUNTIME_PASSWORD", "compose-smoke-test-runtime"),
                java.util.Map.entry("APP_POSTGRES_MIGRATION_PASSWORD", "compose-smoke-test-migration"),
                java.util.Map.entry("APP_POSTGRES_MIGRATION_USER", "compose_smoke_migrator"),
                java.util.Map.entry("APP_POSTGRES_RUNTIME_USER", "compose_smoke_runtime"),
                java.util.Map.entry("APP_POSTGRES_BOOTSTRAP_USER", "compose_smoke_bootstrap"),
                java.util.Map.entry("APP_POSTGRES_DB", "compose_smoke"),
                java.util.Map.entry("APP_POSTGRES_JDBC_URL", "jdbc:postgresql://app-postgresql:5432/compose_smoke"),
                java.util.Map.entry("APP_POSTGRES_R2DBC_URL", "r2dbc:postgresql://app-postgresql:5432/compose_smoke"),
                java.util.Map.entry("APP_POSTGRES_R2DBC_ENABLED", "false"),
                java.util.Map.entry("APP_POSTGRES_CUTOVER_ENABLED", "false")));
        Process process = processBuilder.start();
        var output = new ByteArrayOutputStream();
        Thread outputReader = new Thread(() -> {
            try (var input = process.getInputStream()) {
                input.transferTo(output);
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to read command output", exception);
            }
        });
        outputReader.start();
        if (!process.waitFor(COMMAND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            throw new AssertionError("Command timed out: " + String.join(" ", command));
        }
        outputReader.join();
        return new CommandResult(process.exitValue(), output.toString());
    }

    private record CommandResult(int exitCode, String stdout) {
    }
}
