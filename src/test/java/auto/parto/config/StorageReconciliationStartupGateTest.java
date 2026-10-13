package auto.parto.config;

import auto.parto.persistence.FileRecord;
import auto.parto.persistence.FileRepository;
import auto.parto.service.StorageReconciliationService;
import auto.parto.storage.FileStorage;
import auto.parto.storage.StoredObject;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.core.io.buffer.DataBuffer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StorageReconciliationStartupGateTest {

    @Test
    void blocksStartupWhenAnActiveObjectIsMissing() {
        FileRecord record = new FileRecord(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
                "objects/missing", "file.jpg", "image/jpeg", 1, "sha", "ACTIVE", Instant.now(), Instant.now());
        StorageReconciliationService service = new StorageReconciliationService(
                new Records(List.of(record)), new MissingStorage(), 10);

        assertThatThrownBy(() -> new StorageReconciliationStartupGate(service, Duration.ofSeconds(1))
                .run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("storage reconciliation failed");
    }

    private static final class Records implements FileRepository {
        private final List<FileRecord> records;
        private Records(List<FileRecord> records) { this.records = records; }
        public Flux<FileRecord> findActive(int limit) { return Flux.fromIterable(records).take(limit); }
        public Mono<FileRecord> createForRequest(UUID a, UUID b, String c, String d, String e, long f, String g) { return Mono.empty(); }
        public Mono<FileRecord> createForListing(UUID a, UUID b, String c, String d, String e, long f, String g) { return Mono.empty(); }
        public Flux<FileRecord> findByRequest(UUID a, int b) { return Flux.empty(); }
        public Flux<FileRecord> findByListing(UUID a, int b) { return Flux.empty(); }
        public Flux<FileRecord> findByPublicIds(List<UUID> a, int b) { return Flux.empty(); }
        public Mono<FileRecord> findByPublicId(UUID a) { return Mono.empty(); }
        public Mono<FileRecord> activate(UUID a, UUID b) { return Mono.empty(); }
        public Mono<Boolean> tombstone(UUID a, UUID b) { return Mono.just(false); }
    }

    private static final class MissingStorage implements FileStorage {
        public Mono<StoredObject> put(String a, Path b, String c, long d, String e) { return Mono.empty(); }
        public Flux<DataBuffer> read(String a) { return Flux.empty(); }
        public Mono<Boolean> exists(String a) { return Mono.just(false); }
        public Mono<Void> delete(String a) { return Mono.empty(); }
    }
}
