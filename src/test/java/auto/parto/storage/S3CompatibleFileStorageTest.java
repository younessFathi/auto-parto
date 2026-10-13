package auto.parto.storage;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class S3CompatibleFileStorageTest {

    @Test
    void rejectsMissingCredentialsBeforeCreatingProviderClient() {
        assertThatThrownBy(() -> new S3CompatibleFileStorage(URI.create("http://localhost:8333"),
                "us-east-1", "auto-parto-files", "", "secret", 32))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("accessKey");
    }

    @Test
    void rejectsUnboundedConcurrencyConfiguration() {
        assertThatThrownBy(() -> new S3CompatibleFileStorage(URI.create("http://localhost:8333"),
                "us-east-1", "auto-parto-files", "access", "secret", 257))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxConcurrency");
    }
}
