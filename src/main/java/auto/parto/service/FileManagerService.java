package auto.parto.service;

import auto.parto.dto.FileDto;
import auto.parto.exception.FileUploadException;
import auto.parto.persistence.FileRepository;
import auto.parto.persistence.FileRecord;
import auto.parto.service.iservice.IFileManagerService;
import auto.parto.service.iservice.IFileService;
import auto.parto.storage.FileStorage;
import auto.parto.storage.LocalFileStorage;
import auto.parto.utils.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicLong;
import java.util.UUID;

import static org.apache.commons.io.FilenameUtils.getExtension;

@Service
@Slf4j
public class FileManagerService implements IFileManagerService {
    @Value("${auto.parto.images.uploads}")
    private String uploadsDirectory;
    private IFileService fileService;
    private final FileRepository postgresqlFileRepository;
    private final FileStorage fileStorage;

    public FileManagerService(@Value("${auto.parto.images.uploads}") String uploadsDirectory , IFileService fileService) {
        this(uploadsDirectory, fileService, null, new LocalFileStorage(uploadsDirectory, "test"));
    }

    public FileManagerService(@Value("${auto.parto.images.uploads}") String uploadsDirectory,
                              IFileService fileService, FileRepository postgresqlFileRepository) {
        this(uploadsDirectory, fileService, postgresqlFileRepository,
                new LocalFileStorage(uploadsDirectory, "test"));
    }

    public FileManagerService(String uploadsDirectory, IFileService fileService,
                              FileRepository postgresqlFileRepository, FileStorage fileStorage) {
        this.uploadsDirectory = uploadsDirectory;
        this.fileService = fileService;
        this.postgresqlFileRepository = postgresqlFileRepository;
        this.fileStorage = fileStorage;
    }

    @Autowired
    public FileManagerService(@Value("${auto.parto.images.uploads}") String uploadsDirectory,
                              IFileService fileService,
                              ObjectProvider<FileRepository> postgresqlFileRepositoryProvider,
                              ObjectProvider<FileStorage> fileStorageProvider) {
        this(uploadsDirectory, fileService, postgresqlFileRepositoryProvider.getIfAvailable(),
                fileStorageProvider.getIfAvailable());
    }

    @Override
    public Mono<String> save(FilePart image , String pieceId) {
        log.info("{} : {}" , "Saving File",  image.filename());
        return writeImage(image, pieceId)
                .map(stored -> FileDto.builder()
                                // Create FileDto
                                .createdAt(LocalDateTime.now())
                                .generatedFilename(stored.generatedFilename())
                                .id(UUID.randomUUID().toString())
                                .originalFilename(image.filename())
                                .folderName(pieceId)
                                .pieceId(pieceId)
                                .build())
                .flatMap(fileService::save)
                .map(FileDto::getId)
                // Persist metadata in the database
                .doOnSuccess(file -> log.info("{} : {}", Constants.FileUpload.FILE_SAVED, file));
    }

    @Override
    public Mono<String> saveForRequest(FilePart image, UUID ownerUserPublicId, UUID requestPublicId) {
        return saveForTarget(image, ownerUserPublicId, requestPublicId, true);
    }

    @Override
    public Mono<String> saveForListing(FilePart image, UUID ownerUserPublicId, UUID listingPublicId) {
        return saveForTarget(image, ownerUserPublicId, listingPublicId, false);
    }

    private Mono<String> saveForTarget(FilePart image, UUID ownerUserPublicId, UUID targetPublicId,
                                       boolean requestTarget) {
        if (postgresqlFileRepository == null) {
            return Mono.error(new IllegalStateException("PostgreSQL file persistence is not enabled"));
        }
        if (ownerUserPublicId == null || targetPublicId == null) {
            return Mono.error(new IllegalArgumentException("owner and target IDs are required"));
        }
        if (fileStorage == null) {
            return Mono.error(new IllegalStateException("File storage is not enabled"));
        }
        return writeStagedImage(image)
                .flatMap(staged -> {
                    String objectKey = targetPublicId + "/" + staged.generatedFilename();
                    Mono<FileRecord> metadata = requestTarget
                            ? postgresqlFileRepository.createForRequest(ownerUserPublicId, targetPublicId,
                            objectKey, image.filename(), contentType(image), staged.byteSize(), staged.checksum())
                            : postgresqlFileRepository.createForListing(ownerUserPublicId, targetPublicId,
                            objectKey, image.filename(), contentType(image), staged.byteSize(), staged.checksum());
                    return metadata
                            .switchIfEmpty(Mono.error(new IllegalArgumentException("target is not owned by the authenticated user")))
                            .flatMap(record -> fileStorage.put(objectKey, staged.destinationFile(), contentType(image),
                                            staged.byteSize(), staged.checksum())
                                    .then(postgresqlFileRepository.activate(record.publicId(), ownerUserPublicId))
                                    .switchIfEmpty(Mono.error(new IllegalStateException("file activation failed")))
                                    .map(recordAfterActivation -> recordAfterActivation.publicId().toString())
                                    .delayUntil(ignored -> deleteStagedFile(staged.destinationFile()))
                                    .onErrorResume(error -> fileStorage.delete(objectKey)
                                            .onErrorResume(cleanupError -> Mono.empty())
                                            .then(postgresqlFileRepository.tombstone(record.publicId(), ownerUserPublicId)
                                                    .onErrorResume(cleanupError -> Mono.empty()))
                                            .then(deleteStagedFile(staged.destinationFile()))
                                            .then(Mono.error(error))))
                            .onErrorResume(error -> fileStorage.delete(objectKey)
                                    .onErrorResume(cleanupError -> Mono.empty())
                                    .then(deleteStagedFile(staged.destinationFile()))
                                    .then(Mono.error(error)));
                });
    }

    private Mono<StoredImage> writeStagedImage(FilePart image) {
        return writeImage(image, ".staging/" + UUID.randomUUID());
    }

    private Mono<StoredImage> writeImage(FilePart image, String folderName) {
        if (image == null || image.filename() == null || image.filename().isBlank()) {
            return Mono.error(new FileUploadException(Constants.FileUpload.INVALID_FILE_TYPE_ONLY_IMAGE_FILES_ARE_ALLOWED));
        }
        Path rootLocation = Paths.get(uploadsDirectory, folderName);
        createUploadDirectory(rootLocation);
        String fileExtension = getExtension(image.filename());
        if (!isAllowedExtension(fileExtension)) {
            return Mono.error(new FileUploadException(Constants.FileUpload.INVALID_FILE_TYPE_ONLY_IMAGE_FILES_ARE_ALLOWED));
        }
        String generatedFileName = UUID.randomUUID() + "." + fileExtension;
        Path destinationFile = rootLocation.resolve(generatedFileName).normalize();
        AtomicLong byteSize = new AtomicLong();
        MessageDigest digest = sha256();
        return DataBufferUtils.write(image.content().doOnNext(buffer -> {
                    byteSize.addAndGet(buffer.readableByteCount());
                    digest.update(buffer.asByteBuffer().asReadOnlyBuffer());
                }), destinationFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
                .doOnTerminate(() -> log.info("{} : {}", Constants.FileUpload.FILE_SAVED_TO_DISK, destinationFile))
                .then(Mono.fromSupplier(() -> new StoredImage(generatedFileName, destinationFile, byteSize.get(),
                        HexFormat.of().formatHex(digest.digest()))));
    }

    private static Mono<Void> deleteStagedFile(Path destinationFile) {
        return Mono.fromCallable(() -> {
                    Files.deleteIfExists(destinationFile);
                    return (Void) null;
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    private static String contentType(FilePart image) {
        return image.headers().getContentType() == null
                ? "application/octet-stream" : image.headers().getContentType().toString();
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record StoredImage(String generatedFilename, Path destinationFile, long byteSize, String checksum) {
    }

    private void createUploadDirectory(Path rootLocation) {
        try {
            if (!Files.exists(rootLocation)) {
                Files.createDirectories(rootLocation);
            }
        } catch (IOException e) {
            throw new RuntimeException(Constants.FileUpload.COULD_NOT_INITIALIZE_UPLOAD_FOLDER, e);
        }
    }

    private boolean isAllowedExtension(String fileExtension) {
        return  Constants.FileUpload.ALLOWED_EXTENSIONS.contains(fileExtension.toLowerCase());
    }

}
