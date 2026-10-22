package auto.parto.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DeploymentContractTest {

    @Test
    void applicationConfigUsesBoundedPoolsAndGracefulShutdown() throws IOException {
        String application = Files.readString(Path.of("src/main/resources/application.yml"));

        assertThat(application).contains("server:\n  shutdown: graceful")
                .contains("timeout-per-shutdown-phase: ${APP_SHUTDOWN_TIMEOUT:20s}")
                .contains("max-in-memory-size: ${APP_MAX_IN_MEMORY_SIZE:1MB}")
                .contains("max-size: ${APP_POSTGRES_R2DBC_POOL_MAX_SIZE:10}")
                .contains("max-active: ${APP_REDIS_POOL_MAX_ACTIVE:8}")
                .contains("pool-size: ${APP_RETRY_EXECUTOR_POOL_SIZE:3}")
                .contains("pool-size: ${APP_NOTIFICATION_RETRY_EXECUTOR_POOL_SIZE:2}");
    }

    @Test
    void imageRunsAsNonRootAndDoesNotEmbedRuntimeSecrets() throws IOException {
        String dockerfile = Files.readString(Path.of("Dockerfile"));

        assertThat(dockerfile).contains("USER spring:spring")
                .contains("STOPSIGNAL SIGTERM")
                .contains("ENTRYPOINT [\"java\"")
                .doesNotContain("ENV JWT_SECRET")
                .doesNotContain("ENV PASSWORD")
                .doesNotContain("ENV TOKEN");
    }
}
