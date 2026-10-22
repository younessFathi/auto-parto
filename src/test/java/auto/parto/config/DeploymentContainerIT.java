package auto.parto.config;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.testcontainers.containers.wait.strategy.Wait.forHttp;

class DeploymentContainerIT {

    private static final long MEMORY_LIMIT_BYTES = 1024L * 1024L * 1024L;
    private static final long CPU_LIMIT_NANOS = 1_000_000_000L;
    private static final long PROCESS_LIMIT = 256L;
    private static final Path PROJECT_ROOT = Path.of(".").toAbsolutePath().normalize();
    private static final Network NETWORK = Network.newNetwork();
    private static String imageTag;
    private static MongoDBContainer mongo;
    private static GenericContainer<?> redis;
    private static GenericContainer<?> application;

    @BeforeAll
    static void startDeployment() throws Exception {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is not available");
        imageTag = "auto-parto-deployment-test:" + UUID.randomUUID().toString().replace('-', '_');
        buildApplicationImage();

        mongo = new MongoDBContainer("mongo:7.0")
                .withNetwork(NETWORK)
                .withNetworkAliases("mongo");
        redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                .withNetwork(NETWORK)
                .withNetworkAliases("redis");
        application = new GenericContainer<>(DockerImageName.parse(imageTag))
                .withNetwork(NETWORK)
                .withNetworkAliases("application")
                .withExposedPorts(8080)
                .withEnv("SPRING_PROFILES_ACTIVE", "local")
                .withEnv("SPRING_DATA_MONGODB_URI", "mongodb://mongo:27017/auto-parto")
                .withEnv("SPRING_DATA_REDIS_HOST", "redis")
                .withEnv("SPRING_DATA_REDIS_PORT", "6379")
                .withEnv("REDIS_PASSWORD", "")
                .withEnv("JWT_SECRET", "bmgTK4gXGmWEbq3jc3zluPcMtomqqpMSO1VY7R0QqYQ=")
                .withEnv("OTP_PEPPER", "container-test-pepper")
                .withEnv("FIREBASE_ENABLED", "false")
                .withEnv("APP_FIREBASE_COMPONENTS_ENABLED", "false")
                .withEnv("RESEND_API_KEY", "container-test-disabled")
                .withEnv("APP_FILE_STORAGE_PROVIDER", "local")
                .withEnv("APP_FILE_STORAGE_LOCAL_ROOT", "/tmp/uploads")
                .withEnv("APP_AUDIT_OUTBOX_ENABLED", "false")
                .withTmpFs(Map.of("/tmp", "rw,noexec,nosuid,size=64m"))
                .withCreateContainerCmdModifier(command -> command
                        .withUser("spring:spring")
                        .withReadonlyRootfs(true)
                        .withHostConfig(command.getHostConfig()
                                .withMemory(MEMORY_LIMIT_BYTES)
                                .withNanoCPUs(CPU_LIMIT_NANOS)
                                .withPidsLimit(PROCESS_LIMIT)))
                .waitingFor(forHttp("/actuator/health/liveness")
                        .forPort(8080)
                        .withStartupTimeout(Duration.ofSeconds(90)))
                .withStartupAttempts(1)
                .dependsOn(mongo, redis);

        mongo.start();
        redis.start();
        application.start();
    }

    @AfterAll
    static void stopDeployment() {
        close(application);
        close(redis);
        close(mongo);
        NETWORK.close();
        if (imageTag != null) {
            removeImage();
        }
    }

    @Test
    void actualApplicationImageStartsNonRootWithReadOnlyRootAndBoundedResources() throws Exception {
        var identity = application.execInContainer("id", "-u");
        var writableTmp = application.execInContainer("sh", "-c", "test -w /tmp");

        assertThat(identity.getExitCode()).isZero();
        assertThat(identity.getStdout().trim()).isEqualTo("100");
        assertThat(writableTmp.getExitCode()).isZero();
        assertThat(application.getContainerInfo().getHostConfig().getReadonlyRootfs()).isTrue();
        assertThat(application.getContainerInfo().getHostConfig().getMemory()).isEqualTo(MEMORY_LIMIT_BYTES);
        assertThat(application.getContainerInfo().getHostConfig().getNanoCPUs()).isEqualTo(CPU_LIMIT_NANOS);
        assertThat(application.getContainerInfo().getHostConfig().getPidsLimit()).isEqualTo(PROCESS_LIMIT);
        assertThat(application.getTmpFsMapping()).containsKey("/tmp");
    }

    private static void buildApplicationImage() throws IOException, InterruptedException {
        Process build = new ProcessBuilder(
                "docker", "build", "--file", "Dockerfile", "--tag", imageTag, ".")
                .directory(PROJECT_ROOT.toFile())
                .inheritIO()
                .start();
        assertThat(build.waitFor()).as("Dockerfile build exit code").isZero();
    }

    private static void removeImage() {
        try {
            new ProcessBuilder("docker", "image", "rm", "--force", imageTag)
                    .directory(PROJECT_ROOT.toFile())
                    .redirectErrorStream(true)
                    .start()
                    .waitFor();
        } catch (IOException | InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static void close(AutoCloseable resource) {
        if (resource != null) {
            try {
                resource.close();
            } catch (Exception ignored) {
                // Testcontainers cleanup is best effort after assertions finish.
            }
        }
    }
}
