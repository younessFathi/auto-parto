package auto.parto.config;

import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SecretsBundleDeploymentIT {

    private static final String TEST_IMAGE = "alpine:3.20";
    private static final String OLD_GENERATION = "a".repeat(64);
    private static final String INCOMPLETE_GENERATION = "b".repeat(64);

    @Test
    void applicationIdentityCanReadButCannotWriteMountedBundle() throws Exception {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is not available");
        Path secretRoot = Files.createTempDirectory("auto-parto-secret-generation-");
        try {
            CommandResult prepared = run(List.of("docker", "run", "--rm",
                    "--volume", secretRoot + ":/run/auto-parto", TEST_IMAGE,
                    "sh", "-ec", "mkdir -p /run/auto-parto/generations/" + OLD_GENERATION + "/secrets " +
                            "/run/auto-parto/generations/" + OLD_GENERATION + "/audit; " +
                            "printf 'TEST_MARKER=nonsecret\\n' > /run/auto-parto/generations/" + OLD_GENERATION +
                            "/secrets/secrets.properties; " +
                            "printf 'TEST_AUDIT_MARKER=nonsecret\\n' > /run/auto-parto/generations/" + OLD_GENERATION +
                            "/audit/secrets.properties; " +
                            "chown 0:10001 /run/auto-parto/generations/" + OLD_GENERATION +
                            "/secrets /run/auto-parto/generations/" + OLD_GENERATION + "/audit " +
                            "/run/auto-parto/generations/" + OLD_GENERATION +
                            "/secrets/secrets.properties /run/auto-parto/generations/" + OLD_GENERATION +
                            "/audit/secrets.properties; " +
                            "chmod 0750 /run/auto-parto/generations/" + OLD_GENERATION +
                            "/secrets /run/auto-parto/generations/" + OLD_GENERATION + "/audit; " +
                            "chmod 0440 /run/auto-parto/generations/" + OLD_GENERATION +
                            "/secrets/secrets.properties /run/auto-parto/generations/" + OLD_GENERATION +
                            "/audit/secrets.properties; " +
                            "ln -s generations/" + OLD_GENERATION + " /run/auto-parto/active"));
            assertThat(prepared.exitCode()).as(prepared.output()).isZero();

            CommandResult access = run(List.of("docker", "run", "--rm", "--user", "10001:10001",
                    "--volume", secretRoot.resolve("active") + ":/run/auto-parto:ro", TEST_IMAGE,
                    "sh", "-ec", "test -r /run/auto-parto/secrets/secrets.properties; " +
                            "test ! -w /run/auto-parto/secrets/secrets.properties; " +
                            "cat /run/auto-parto/secrets/secrets.properties >/dev/null; " +
                            "if printf x >> /run/auto-parto/secrets/secrets.properties 2>/dev/null; " +
                            "then exit 9; fi"));
            assertThat(access.exitCode()).as(access.output()).isZero();
        } finally {
            deleteRecursively(secretRoot);
        }
    }

    @Test
    void incompleteLaterBundleCannotSwitchTheActiveGeneration() throws Exception {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is not available");
        Path secretRoot = Files.createTempDirectory("auto-parto-secret-activation-");
        Path activationScript = Path.of("deploy/ansible/activate-secret-generation.sh").toAbsolutePath();
        try {
            CommandResult prepared = run(List.of("docker", "run", "--rm",
                    "--volume", secretRoot + ":/run/auto-parto",
                    "--volume", activationScript + ":/activate-secret-generation.sh:ro", TEST_IMAGE,
                    "sh", "-ec", "mkdir -p /run/auto-parto/generations/" + OLD_GENERATION + "/secrets " +
                            "/run/auto-parto/generations/" + INCOMPLETE_GENERATION + "/secrets; " +
                            "printf 'ACTIVE=old\\n' > /run/auto-parto/generations/" + OLD_GENERATION +
                            "/secrets/secrets.properties; " +
                            "printf 'STAGED=new\\n' > /run/auto-parto/generations/" + INCOMPLETE_GENERATION +
                            "/secrets/secrets.properties; " +
                            "ln -s generations/" + OLD_GENERATION + " /run/auto-parto/active"));
            assertThat(prepared.exitCode()).as(prepared.output()).isZero();

            CommandResult activation = run(List.of("docker", "run", "--rm",
                    "--volume", secretRoot + ":/run/auto-parto",
                    "--volume", activationScript + ":/activate-secret-generation.sh:ro", TEST_IMAGE,
                    "sh", "-ec", "if sh /activate-secret-generation.sh /run/auto-parto " +
                            INCOMPLETE_GENERATION + " secrets/secrets.properties audit/secrets.properties; " +
                            "then exit 9; fi; " +
                            "test \"$(readlink /run/auto-parto/active)\" = generations/" + OLD_GENERATION + "; " +
                            "grep -q 'ACTIVE=old' /run/auto-parto/active/secrets/secrets.properties"));
            assertThat(activation.exitCode()).as(activation.output()).isZero();

            CommandResult rejectedPath = run(List.of("docker", "run", "--rm",
                    "--volume", secretRoot + ":/run/auto-parto",
                    "--volume", activationScript + ":/activate-secret-generation.sh:ro", TEST_IMAGE,
                    "sh", "-ec", "if sh /activate-secret-generation.sh /run/auto-parto " + OLD_GENERATION +
                            " secrets/secrets.properties ../../etc/passwd; then exit 9; fi; " +
                            "test \"$(readlink /run/auto-parto/active)\" = generations/" + OLD_GENERATION));
            assertThat(rejectedPath.exitCode()).as(rejectedPath.output()).isZero();
        } finally {
            deleteRecursively(secretRoot);
        }
    }

    private static CommandResult run(List<String> command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        var output = new ByteArrayOutputStream();
        Thread reader = new Thread(() -> {
            try (var input = process.getInputStream()) {
                input.transferTo(output);
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to read Docker test output", exception);
            }
        });
        reader.start();
        if (!process.waitFor(Duration.ofMinutes(2).toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            throw new AssertionError("Docker command timed out: " + String.join(" ", command));
        }
        reader.join();
        return new CommandResult(process.exitValue(), output.toString());
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (Files.exists(root)) {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private record CommandResult(int exitCode, String output) {
    }
}
