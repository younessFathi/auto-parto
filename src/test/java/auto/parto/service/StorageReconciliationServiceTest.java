package auto.parto.service;

import auto.parto.persistence.FileRecord;
import auto.parto.persistence.FileRepository;
import auto.parto.storage.FileStorage;
import auto.parto.storage.StoredObject;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StorageReconciliationServiceTest {

    @Test
    void reportsMissingObjectsAndChecksMetadataSequentially() {
        FileRecord present = record("objects/present");
        FileRecord missing = record("objects/missing");
        FileRepository repository = new StubRepository(List.of(present, missing));
        FileStorage storage = new StubStorage();

        StepVerifier.create(new StorageReconciliationService(repository, storage, 10).reconcile())
                .assertNext(report -> {
                    assertThat(report.checkedFiles()).isEqualTo(2);
                    assertThat(report.missingObjectKeys()).containsExactly("objects/missing");
                    assertThat(report.healthy()).isFalse();
                }).verifyComplete();
    }

    @Test
    void acceptsEmptyActiveFileSet() {
        StepVerifier.create(new StorageReconciliationService(new StubRepository(List.of()), new StubStorage(), 1).reconcile())
                .assertNext(report -> assertThat(report.healthy()).isTrue())
                .verifyComplete();
    }

    @Test
    void failsClosedWhenActiveFileCountExceedsBoundedScan() {
        FileRepository repository = new StubRepository(List.of(record("objects/missing-later")), 2);
        FileStorage storage = new StubStorage();

        StepVerifier.create(new StorageReconciliationService(repository, storage, 1).reconcile())
                .assertNext(report -> {
                    assertThat(report.capacityExceeded()).isTrue();
                    assertThat(report.healthy()).isFalse();
                }).verifyComplete();
    }

    @Test
    void reportsOrphanObjectsAndCanRemediateThemIdempotently() {
        FileRecord present = record("objects/present");
        StubStorage storage = new StubStorage(List.of("objects/present", "objects/orphan"));

        StepVerifier.create(new StorageReconciliationService(new StubRepository(List.of(present)), storage,
                        10, true).reconcile())
                .assertNext(report -> {
                    assertThat(report.orphanObjectKeys()).containsExactly("objects/orphan");
                    assertThat(report.orphanDeletionEnabled()).isTrue();
                    assertThat(report.healthy()).isFalse();
                    assertThat(storage.deleted).containsExactly("objects/orphan");
                }).verifyComplete();
    }

    @Test
    void failsClosedWhenProviderScanIsTruncated() {
        FileRecord present = record("objects/present");
        StubStorage storage = new StubStorage(List.of("objects/present", "objects/orphan"));

        StepVerifier.create(new StorageReconciliationService(new StubRepository(List.of(present)), storage, 1).reconcile())
                .assertNext(report -> {
                    assertThat(report.capacityExceeded()).isTrue();
                    assertThat(report.healthy()).isFalse();
                    assertThat(report.orphanObjectKeys()).containsExactly("objects/present");
                    assertThat(storage.deleted).isEmpty();
                }).verifyComplete();
    }

    private static FileRecord record(String key) {
        return new FileRecord(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, key,
                "file.jpg", "image/jpeg", 1, "sha", "ACTIVE", Instant.now(), Instant.now());
    }

    private static final class StubRepository implements FileRepository {
        private final List<FileRecord> records;
        private final long activeCount;
        private StubRepository(List<FileRecord> records) { this(records, records.size()); }
        private StubRepository(List<FileRecord> records, long activeCount) {
            this.records = records;
            this.activeCount = activeCount;
        }
        public Flux<FileRecord> findActive(int limit) { return Flux.fromIterable(records).take(limit); }
        public Mono<Long> countActive() { return Mono.just(activeCount); }
        public Mono<FileRecord> createForRequest(UUID a, UUID b, String c, String d, String e, long f, String g) { return Mono.empty(); }
        public Mono<FileRecord> createForListing(UUID a, UUID b, String c, String d, String e, long f, String g) { return Mono.empty(); }
        public Flux<FileRecord> findByRequest(UUID a, int b) { return Flux.empty(); }
        public Flux<FileRecord> findByListing(UUID a, int b) { return Flux.empty(); }
        public Flux<FileRecord> findByPublicIds(List<UUID> a, int b) { return Flux.empty(); }
        public Mono<FileRecord> findByPublicId(UUID a) { return Mono.empty(); }
        public Mono<FileRecord> activate(UUID a, UUID b) { return Mono.empty(); }
        public Mono<Boolean> tombstone(UUID a, UUID b) { return Mono.just(false); }
    }

    private static final class StubStorage implements FileStorage {
        private final List<String> keys;
        private final List<String> deleted = new java.util.ArrayList<>();
        private StubStorage() { this(List.of()); }
        private StubStorage(List<String> keys) { this.keys = keys; }
        public Mono<StoredObject> put(String a, Path b, String c, long d, String e) { return Mono.empty(); }
        public Flux<DataBuffer> read(String a) { return Flux.empty(); }
        public Mono<Boolean> exists(String key) { return Mono.just("objects/present".equals(key)); }
        public Mono<Void> delete(String a) { deleted.add(a); return Mono.empty(); }
        public Flux<String> listObjectKeys(int limit) { return Flux.fromIterable(keys).take(limit); }
    }
}
