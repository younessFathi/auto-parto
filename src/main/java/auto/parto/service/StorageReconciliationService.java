package auto.parto.service;

import auto.parto.persistence.FileRecord;
import auto.parto.persistence.FileRepository;
import auto.parto.storage.FileStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Set;

/** Verifies that active PostgreSQL file metadata has a matching storage object. */
@Service
@ConditionalOnProperty(prefix = "auto.parto.persistence.postgresql.cutover", name = "enabled",
        havingValue = "true")
public final class StorageReconciliationService {

    private final FileRepository fileRepository;
    private final FileStorage fileStorage;
    private final int maxFiles;
    private final boolean deleteOrphans;

    public StorageReconciliationService(FileRepository fileRepository, FileStorage fileStorage,
                                        @Value("${auto.parto.persistence.postgresql.cutover.storage-reconciliation.max-files:1000}") int maxFiles) {
        this(fileRepository, fileStorage, maxFiles, false);
    }

    @Autowired
    public StorageReconciliationService(FileRepository fileRepository, FileStorage fileStorage,
                                        @Value("${auto.parto.persistence.postgresql.cutover.storage-reconciliation.max-files:1000}") int maxFiles,
                                        @Value("${auto.parto.persistence.postgresql.cutover.storage-reconciliation.delete-orphans:false}") boolean deleteOrphans) {
        if (maxFiles < 1 || maxFiles > 1000) throw new IllegalArgumentException("max-files must be between 1 and 1000");
        this.fileRepository = fileRepository;
        this.fileStorage = fileStorage;
        this.maxFiles = maxFiles;
        this.deleteOrphans = deleteOrphans;
    }

    public Mono<StorageReconciliationReport> reconcile() {
        return fileRepository.countActive().flatMap(activeCount -> {
            if (activeCount > maxFiles) {
                return Mono.just(new StorageReconciliationReport(0, List.of(), List.of(), true,
                        deleteOrphans));
            }
            Mono<List<CheckedFile>> checked = fileRepository.findActive(maxFiles)
                    .concatMap(record -> fileStorage.exists(record.objectKey())
                            .map(exists -> new CheckedFile(record, exists)))
                    .collectList();
            Mono<List<String>> objects = fileStorage.listObjectKeys(maxFiles).collectList();
            return Mono.zip(checked, objects).flatMap(result -> {
                List<CheckedFile> checkedFiles = result.getT1();
                Set<String> activeKeys = checkedFiles.stream().map(file -> file.record().objectKey()).collect(java.util.stream.Collectors.toSet());
                List<String> orphans = result.getT2().stream().filter(key -> !activeKeys.contains(key)).toList();
                Mono<Void> remediation = deleteOrphans
                        ? Flux.fromIterable(orphans).concatMap(fileStorage::delete).then()
                        : Mono.empty();
                return remediation.thenReturn(report(checkedFiles, orphans, false));
            });
        });
    }

    private StorageReconciliationReport report(List<CheckedFile> checkedFiles, List<String> orphans,
                                               boolean capacityExceeded) {
        List<String> missing = checkedFiles.stream().filter(file -> !file.exists())
                .map(file -> file.record().objectKey()).toList();
        return new StorageReconciliationReport(checkedFiles.size(), missing, orphans, capacityExceeded,
                deleteOrphans);
    }

    private record CheckedFile(FileRecord record, boolean exists) {}

    public record StorageReconciliationReport(int checkedFiles, List<String> missingObjectKeys,
                                              List<String> orphanObjectKeys, boolean capacityExceeded,
                                              boolean orphanDeletionEnabled) {
        public StorageReconciliationReport(int checkedFiles, List<String> missingObjectKeys) {
            this(checkedFiles, missingObjectKeys, List.of(), false, false);
        }

        public StorageReconciliationReport {
            missingObjectKeys = List.copyOf(missingObjectKeys);
            orphanObjectKeys = List.copyOf(orphanObjectKeys);
        }

        public boolean healthy() {
            return !capacityExceeded && missingObjectKeys.isEmpty() && orphanObjectKeys.isEmpty();
        }
    }
}
