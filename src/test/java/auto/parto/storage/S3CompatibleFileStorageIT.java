package auto.parto.storage;

import auto.parto.config.ProviderCallTelemetry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class S3CompatibleFileStorageIT {

    private static final String ACCESS_KEY = "foo";
    private static final String SECRET_KEY = "bar";
    private static final String BUCKET = "auto-parto-files";
    private static final String OBJECT_KEY = "provider-isolation/test-object.txt";

    @Container
    private static final GenericContainer<?> MINIO = new GenericContainer<>(
            DockerImageName.parse("adobe/s3mock:5.2.3"))
            .withEnv("COM_ADOBE_TESTING_S3MOCK_STORE_INITIAL_BUCKETS", BUCKET)
            .withExposedPorts(9090)
            .waitingFor(Wait.forHttp("/favicon.ico").forPort(9090).forStatusCode(200));

    private S3CompatibleFileStorage storage;
    private SimpleMeterRegistry registry;
    private Path stagedFile;

    @BeforeEach
    void startS3Storage() throws Exception {
        URI endpoint = URI.create("http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9090));
        registry = new SimpleMeterRegistry();
        storage = new S3CompatibleFileStorage(endpoint, "us-east-1", BUCKET, ACCESS_KEY, SECRET_KEY,
                4, 8, Duration.ofSeconds(5), new ProviderCallTelemetry(registry));
        stagedFile = Files.createTempFile("auto-parto-s3-it-", ".txt");
        Files.writeString(stagedFile, "provider boundary integration payload");
    }

    @AfterEach
    void closeS3Storage() throws Exception {
        if (storage != null) storage.close();
        if (registry != null) registry.close();
        if (stagedFile != null) Files.deleteIfExists(stagedFile);
    }

    @Test
    void asyncS3OperationsRemainReactiveAndEmitBoundedProviderMetrics() {
        String contents = "provider boundary integration payload";
        assertThat(storage.put(OBJECT_KEY, stagedFile, "text/plain", contents.length(), "test-sha256")
                .block(Duration.ofSeconds(5)).objectKey()).isEqualTo(OBJECT_KEY);
        assertThat(storage.exists(OBJECT_KEY).block(Duration.ofSeconds(5))).isTrue();
        List<String> keys = storage.listObjectKeys(10).collectList().block(Duration.ofSeconds(5));
        assertThat(keys).contains(OBJECT_KEY);

        byte[] bytes = DataBufferUtils.join(storage.read(OBJECT_KEY))
                .map(buffer -> {
                    byte[] value = new byte[buffer.readableByteCount()];
                    buffer.read(value);
                    DataBufferUtils.release(buffer);
                    return value;
                }).block(Duration.ofSeconds(5));
        assertThat(new String(bytes)).isEqualTo(contents);

        long successfulOperations = registry.find("auto.parto.provider.calls")
                .tag("provider", "s3").tag("outcome", "success")
                .timers().stream().mapToLong(Timer::count).sum();
        assertThat(successfulOperations).isGreaterThanOrEqualTo(4);
    }
}
