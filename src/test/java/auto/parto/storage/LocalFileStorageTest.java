package auto.parto.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.buffer.DataBufferUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFileStorageTest {

    @TempDir
    Path root;

    @Test
    void storesReadsAndDeletesObjectWithoutExposingBytesToMetadata() throws Exception {
        LocalFileStorage storage = new LocalFileStorage(root.toString(), "test");
        Path staged = root.resolve("staged.bin");
        Files.writeString(staged, "payload");

        StoredObject stored = storage.put("requests/request-1/file-1.jpg", staged,
                "image/jpeg", 7, "checksum").block();

        assertThat(stored).isEqualTo(new StoredObject("requests/request-1/file-1.jpg", 7, "checksum"));
        assertThat(DataBufferUtils.join(storage.read(stored.objectKey())).block().toString(java.nio.charset.StandardCharsets.UTF_8))
                .isEqualTo("payload");
        storage.delete(stored.objectKey()).block();
        assertThat(Files.exists(root.resolve(stored.objectKey()))).isFalse();
        assertThat(storage.exists(stored.objectKey()).block()).isFalse();
    }

    @Test
    void rejectsUnsafeObjectKeys() {
        LocalFileStorage storage = new LocalFileStorage(root.toString(), "test");

        assertThatThrownBy(() -> storage.read("../secret").blockLast())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsLocalStorageInProductionProfile() {
        LocalFileStorage storage = new LocalFileStorage(root.toString(), "prod");

        assertThatThrownBy(storage::rejectProductionLocalStorage)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be used");
    }

    @Test
    void listsBoundedObjectKeysAndSkipsStagingFiles() throws Exception {
        LocalFileStorage storage = new LocalFileStorage(root.toString(), "test");
        Files.createDirectories(root.resolve("objects"));
        Files.createDirectories(root.resolve(".staging/job"));
        Files.writeString(root.resolve("objects/a"), "a");
        Files.writeString(root.resolve("objects/b"), "b");
        Files.writeString(root.resolve(".staging/job/temp"), "temp");

        assertThat(storage.listObjectKeys(1).collectList().block()).containsExactly("objects/a");
    }
}
