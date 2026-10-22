package auto.parto.config;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class DeploymentContainerIT {

    private static final long MEMORY_LIMIT_BYTES = 1024L * 1024L * 1024L;
    private static final long CPU_LIMIT_NANOS = 1_000_000_000L;
    private static final long PROCESS_LIMIT = 256L;

    @Container
    private static final GenericContainer<?> APPLICATION = new GenericContainer<>(
            DockerImageName.parse("alpine:3.20"))
            .withCommand("sh", "-c", "id -u && sleep 30")
            .withCreateContainerCmdModifier(command -> {
                command.withUser("1000:1000");
                command.getHostConfig()
                        .withMemory(MEMORY_LIMIT_BYTES)
                        .withNanoCPUs(CPU_LIMIT_NANOS)
                        .withPidsLimit(PROCESS_LIMIT);
            });

    @Test
    void containerRunsNonRootWithBoundedResources() throws Exception {
        var identity = APPLICATION.execInContainer("id", "-u");

        assertThat(identity.getExitCode()).isZero();
        assertThat(identity.getStdout().trim()).isEqualTo("1000");
        assertThat(APPLICATION.getContainerInfo().getHostConfig().getMemory()).isEqualTo(MEMORY_LIMIT_BYTES);
        assertThat(APPLICATION.getContainerInfo().getHostConfig().getNanoCPUs()).isEqualTo(CPU_LIMIT_NANOS);
        assertThat(APPLICATION.getContainerInfo().getHostConfig().getPidsLimit()).isEqualTo(PROCESS_LIMIT);
    }
}
