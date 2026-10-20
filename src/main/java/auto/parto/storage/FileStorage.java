package auto.parto.storage;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.file.Path;

/** Provider-neutral boundary for uploaded object bytes. */
public interface FileStorage {

    Mono<StoredObject> put(String objectKey, Path stagedFile, String contentType,
                           long byteSize, String checksum);

    Flux<org.springframework.core.io.buffer.DataBuffer> read(String objectKey);

    Mono<Boolean> exists(String objectKey);

    Mono<Void> delete(String objectKey);

    /** Returns at most {@code limit} object keys for a bounded reconciliation scan. */
    default Flux<String> listObjectKeys(int limit) {
        if (limit < 1 || limit > 1000) {
            return Flux.error(new IllegalArgumentException("limit must be between 1 and 1000"));
        }
        return Flux.empty();
    }
}
