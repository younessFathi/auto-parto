package auto.parto.storage;

import auto.parto.config.ProviderCallTelemetry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class S3CompatibleFileStorageTest {

    @Test
    void rejectsMissingCredentialsBeforeCreatingProviderClient() {
        assertThatThrownBy(() -> new S3CompatibleFileStorage(URI.create("http://localhost:8333"),
                "us-east-1", "auto-parto-files", "", "secret", 32, 64,
                Duration.ofSeconds(1), new ProviderCallTelemetry(new SimpleMeterRegistry())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("accessKey");
    }

    @Test
    void rejectsUnboundedConcurrencyConfiguration() {
        assertThatThrownBy(() -> new S3CompatibleFileStorage(URI.create("http://localhost:8333"),
                "us-east-1", "auto-parto-files", "access", "secret", 257, 64,
                Duration.ofSeconds(1), new ProviderCallTelemetry(new SimpleMeterRegistry())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxConcurrency");
    }

    @Test
    void rejectsUnboundedPendingRequestConfiguration() {
        assertThatThrownBy(() -> new S3CompatibleFileStorage(URI.create("http://localhost:8333"),
                "us-east-1", "auto-parto-files", "access", "secret", 32, 1025,
                Duration.ofSeconds(1), new ProviderCallTelemetry(new SimpleMeterRegistry())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxPendingRequests");
    }
}
