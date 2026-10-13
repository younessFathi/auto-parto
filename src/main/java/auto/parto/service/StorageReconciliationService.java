package auto.parto.service;

import auto.parto.persistence.FileRecord;
import auto.parto.persistence.FileRepository;
import auto.parto.storage.FileStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

/** Verifies that active PostgreSQL file metadata has a matching storage object. */
@Service
@ConditionalOnProperty(prefix = "auto.parto.persistence.postgresql.cutover", name = "enabled",
        havingValue = "true")
public final class StorageReconciliationService {

    private final FileRepository fileRepository;
    private final FileStorage fileStorage;
    private final int maxFiles;

    public StorageReconciliationService(FileRepository fileRepository, FileStorage fileStorage,
                                        @Value("${auto.parto.persistence.postgresql.cutover.storage-reconciliation.max-files:1000}") int maxFiles) {
        if (maxFiles < 1 || maxFiles > 1000) throw new IllegalArgumentException("max-files must be between 1 and 1000");
        this.fileRepository = fileRepository;
        this.fileStorage = fileStorage;
        this.maxFiles = maxFiles;
    }

    public Mono<StorageReconciliationReport> reconcile() {
        return fileRepository.findActive(maxFiles)
                .concatMap(record -> fileStorage.exists(record.objectKey())
                        .map(exists -> new CheckedFile(record, exists)))
                .collectList()
                .map(this::report);
    }

    private StorageReconciliationReport report(List<CheckedFile> checkedFiles) {
        List<String> missing = checkedFiles.stream().filter(file -> !file.exists())
                .map(file -> file.record().objectKey()).toList();
        return new StorageReconciliationReport(checkedFiles.size(), missing);
    }

    private record CheckedFile(FileRecord record, boolean exists) {}

    public record StorageReconciliationReport(int checkedFiles, List<String> missingObjectKeys) {
        public StorageReconciliationReport {
            missingObjectKeys = List.copyOf(missingObjectKeys);
        }

        public boolean healthy() {
            return missingObjectKeys.isEmpty();
        }
    }
}
