package auto.parto.service;

import auto.parto.persistence.FileRecord;
import auto.parto.persistence.FileRepository;
import auto.parto.storage.FileStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Set;
import java.time.Duration;
import java.time.Instant;

/** Verifies that active PostgreSQL file metadata has a matching storage object. */
@Service
@ConditionalOnExpression("${auto.parto.persistence.postgresql.cutover.enabled:false} && "
        + "${auto.parto.persistence.postgresql.cutover.storage-reconciliation.enabled:false}")
public final class StorageReconciliationService {

    private final FileRepository fileRepository;
    private final FileStorage fileStorage;
    private final int maxFiles;
    private final boolean deleteOrphans;
    private final Duration pendingTimeout;

    public StorageReconciliationService(FileRepository fileRepository, FileStorage fileStorage,
                                        @Value("${auto.parto.persistence.postgresql.cutover.storage-reconciliation.max-files:1000}") int maxFiles) {
        this(fileRepository, fileStorage, maxFiles, false, Duration.ofHours(1));
    }

    @Autowired
    public StorageReconciliationService(FileRepository fileRepository, FileStorage fileStorage,
                                        @Value("${auto.parto.persistence.postgresql.cutover.storage-reconciliation.max-files:1000}") int maxFiles,
                                        @Value("${auto.parto.persistence.postgresql.cutover.storage-reconciliation.delete-orphans:false}") boolean deleteOrphans,
                                        @Value("${auto.parto.persistence.postgresql.cutover.storage-reconciliation.pending-timeout:1h}") Duration pendingTimeout) {
        if (maxFiles < 1 || maxFiles > 1000) throw new IllegalArgumentException("max-files must be between 1 and 1000");
        if (pendingTimeout == null || pendingTimeout.isNegative() || pendingTimeout.isZero()) {
            throw new IllegalArgumentException("pending-timeout must be positive");
        }
        this.fileRepository = fileRepository;
        this.fileStorage = fileStorage;
        this.maxFiles = maxFiles;
        this.deleteOrphans = deleteOrphans;
        this.pendingTimeout = pendingTimeout;
    }

    public StorageReconciliationService(FileRepository fileRepository, FileStorage fileStorage,
                                        int maxFiles, boolean deleteOrphans) {
        this(fileRepository, fileStorage, maxFiles, deleteOrphans, Duration.ofHours(1));
    }

    public Mono<StorageReconciliationReport> reconcile() {
        return recoverStalePending().flatMap(pendingRecovery -> fileRepository.countActive().flatMap(activeCount -> {
            if (activeCount > maxFiles) {
                return Mono.just(new StorageReconciliationReport(0, List.of(), List.of(), true,
                        deleteOrphans));
            }
            Mono<List<CheckedFile>> checked = fileRepository.findActive(maxFiles)
                    .concatMap(record -> fileStorage.exists(record.objectKey())
                            .map(exists -> new CheckedFile(record, exists)))
                    .collectList();
            Mono<FileStorage.ObjectKeyScan> objects = fileStorage.scanObjectKeys(maxFiles);
            return Mono.zip(checked, objects).flatMap(result -> {
                List<CheckedFile> checkedFiles = result.getT1();
                Set<String> activeKeys = checkedFiles.stream().map(file -> file.record().objectKey()).collect(java.util.stream.Collectors.toSet());
                FileStorage.ObjectKeyScan scan = result.getT2();
                if (scan.truncated()) {
                    return Mono.just(new StorageReconciliationReport(checkedFiles.size(),
                            missingKeys(checkedFiles), scan.keys(), true, deleteOrphans));
                }
                List<String> orphans = scan.keys().stream().filter(key -> !activeKeys.contains(key)).toList();
                Mono<Void> remediation = deleteOrphans
                        ? Flux.fromIterable(orphans).concatMap(fileStorage::delete).then()
                        : Mono.empty();
                return remediation.thenReturn(report(checkedFiles, orphans, pendingRecovery.capacityExceeded()));
            });
        }));
    }

    private Mono<PendingRecovery> recoverStalePending() {
        return fileRepository.findStalePending(Instant.now().minus(pendingTimeout), maxFiles + 1)
                .collectList()
                .flatMap(stale -> Flux.fromIterable(stale.stream().limit(maxFiles).toList())
                        .concatMap(record -> fileRepository.tombstonePending(record.publicId(), record.ownerUserPublicId())
                                .flatMap(tombstoned -> tombstoned
                                        ? fileStorage.delete(record.objectKey()).thenReturn(record)
                                        : Mono.empty()))
                        .then(Mono.just(new PendingRecovery(stale.size() > maxFiles))));
    }

    private static List<String> missingKeys(List<CheckedFile> checkedFiles) {
        return checkedFiles.stream().filter(file -> !file.exists())
                .map(file -> file.record().objectKey()).toList();
    }

    private StorageReconciliationReport report(List<CheckedFile> checkedFiles, List<String> orphans,
                                               boolean capacityExceeded) {
        List<String> missing = checkedFiles.stream().filter(file -> !file.exists())
                .map(file -> file.record().objectKey()).toList();
        return new StorageReconciliationReport(checkedFiles.size(), missing, orphans, capacityExceeded,
                deleteOrphans);
    }

    private record CheckedFile(FileRecord record, boolean exists) {}

    private record PendingRecovery(boolean capacityExceeded) {}

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
