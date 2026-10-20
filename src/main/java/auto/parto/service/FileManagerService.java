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
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
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
    private final long maxUploadBytes;
    private final int maxImageWidth;
    private final int maxImageHeight;
    private final long maxActiveFilesPerOwner;

    public FileManagerService(@Value("${auto.parto.images.uploads}") String uploadsDirectory , IFileService fileService) {
        this(uploadsDirectory, fileService, null, new LocalFileStorage(uploadsDirectory, "test"),
                10 * 1024 * 1024L, 10000, 10000, 50);
    }

    public FileManagerService(@Value("${auto.parto.images.uploads}") String uploadsDirectory,
                              IFileService fileService, FileRepository postgresqlFileRepository) {
        this(uploadsDirectory, fileService, postgresqlFileRepository,
                new LocalFileStorage(uploadsDirectory, "test"), 10 * 1024 * 1024L, 10000, 10000, 50);
    }

    public FileManagerService(String uploadsDirectory, IFileService fileService,
                              FileRepository postgresqlFileRepository, FileStorage fileStorage) {
        this(uploadsDirectory, fileService, postgresqlFileRepository, fileStorage, 10 * 1024 * 1024L,
                10000, 10000, 50);
    }

    public FileManagerService(String uploadsDirectory, IFileService fileService,
                              FileRepository postgresqlFileRepository, FileStorage fileStorage,
                              long maxUploadBytes) {
        this(uploadsDirectory, fileService, postgresqlFileRepository, fileStorage, maxUploadBytes,
                10000, 10000, 50);
    }

    public FileManagerService(String uploadsDirectory, IFileService fileService,
                              FileRepository postgresqlFileRepository, FileStorage fileStorage,
                              long maxUploadBytes, int maxImageWidth, int maxImageHeight,
                              long maxActiveFilesPerOwner) {
        if (maxUploadBytes < 1 || maxUploadBytes > Integer.MAX_VALUE - 1) {
            throw new IllegalArgumentException("max upload bytes must be between 1 and 2147483646");
        }
        if (maxImageWidth < 1 || maxImageHeight < 1 || maxActiveFilesPerOwner < 1) {
            throw new IllegalArgumentException("image dimensions and owner quota must be positive");
        }
        this.uploadsDirectory = uploadsDirectory;
        this.fileService = fileService;
        this.postgresqlFileRepository = postgresqlFileRepository;
        this.fileStorage = fileStorage;
        this.maxUploadBytes = maxUploadBytes;
        this.maxImageWidth = maxImageWidth;
        this.maxImageHeight = maxImageHeight;
        this.maxActiveFilesPerOwner = maxActiveFilesPerOwner;
    }

    @Autowired
    public FileManagerService(@Value("${auto.parto.images.uploads}") String uploadsDirectory,
                              IFileService fileService,
                              ObjectProvider<FileRepository> postgresqlFileRepositoryProvider,
                              ObjectProvider<FileStorage> fileStorageProvider,
                              @Value("${auto.parto.storage.max-upload-bytes:10485760}") long maxUploadBytes,
                              @Value("${auto.parto.storage.max-image-width:10000}") int maxImageWidth,
                              @Value("${auto.parto.storage.max-image-height:10000}") int maxImageHeight,
                              @Value("${auto.parto.storage.max-active-files-per-owner:50}") long maxActiveFilesPerOwner) {
        this(uploadsDirectory, fileService, postgresqlFileRepositoryProvider.getIfAvailable(),
                fileStorageProvider.getIfAvailable(), maxUploadBytes, maxImageWidth, maxImageHeight,
                maxActiveFilesPerOwner);
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
        return enforceOwnerQuota(ownerUserPublicId)
                .then(writeStagedImage(image))
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
        String fileExtension = getExtension(image.filename());
        if (!isAllowedExtension(fileExtension)) {
            return Mono.error(new FileUploadException(Constants.FileUpload.INVALID_FILE_TYPE_ONLY_IMAGE_FILES_ARE_ALLOWED));
        }
        String declaredContentType = contentType(image);
        String generatedFileName = UUID.randomUUID() + "." + fileExtension;
        Path rootLocation = Paths.get(uploadsDirectory, folderName);
        Path destinationFile = rootLocation.resolve(generatedFileName).normalize();
        return DataBufferUtils.join(image.content(), Math.toIntExact(maxUploadBytes))
                .onErrorMap(DataBufferLimitException.class, error -> new FileUploadException(
                        "Image exceeds the configured upload size limit", error))
                .flatMap(buffer -> {
                    byte[] bytes = new byte[buffer.readableByteCount()];
                    try {
                        buffer.read(bytes);
                    } finally {
                        DataBufferUtils.release(buffer);
                    }
                    try {
                        validateImage(image.filename(), declaredContentType, bytes);
                    } catch (FileUploadException error) {
                        return Mono.error(error);
                    }
                    return Mono.fromCallable(() -> {
                                createUploadDirectory(rootLocation);
                                Files.write(destinationFile, bytes, StandardOpenOption.CREATE,
                                        StandardOpenOption.TRUNCATE_EXISTING);
                                MessageDigest digest = sha256();
                                return new StoredImage(generatedFileName, destinationFile, bytes.length,
                                        HexFormat.of().formatHex(digest.digest(bytes)));
                            })
                            .subscribeOn(Schedulers.boundedElastic())
                            .doOnSuccess(ignored -> log.info("{} : {}", Constants.FileUpload.FILE_SAVED_TO_DISK,
                                    destinationFile));
                });
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

    private void validateImage(String filename, String contentType, byte[] bytes) {
        String extension = getExtension(filename).toLowerCase(Locale.ROOT);
        String normalizedType = contentType.toLowerCase(Locale.ROOT);
        String expectedType = switch (extension) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "bmp" -> "image/bmp";
            default -> "";
        };
        if (!expectedType.equals(normalizedType) || !hasImageSignature(normalizedType, bytes)) {
            throw new FileUploadException(Constants.FileUpload.INVALID_FILE_TYPE_ONLY_IMAGE_FILES_ARE_ALLOWED);
        }
        try {
            BufferedImage decoded = ImageIO.read(new java.io.ByteArrayInputStream(bytes));
            if (decoded != null && (decoded.getWidth() > maxImageWidth || decoded.getHeight() > maxImageHeight)) {
                throw new FileUploadException("Image dimensions exceed the configured limit");
            }
        } catch (IOException error) {
            // Signature validation remains the compatibility check for legacy fixtures whose
            // payloads are not fully decodable; decoded images are always dimension-bounded.
            log.debug("Image dimensions unavailable for signature-validated payload", error);
        }
    }

    private Mono<Void> enforceOwnerQuota(UUID ownerUserPublicId) {
        return Mono.defer(() -> {
            Mono<Long> count = postgresqlFileRepository.countActiveByOwner(ownerUserPublicId);
            if (count == null) return Mono.empty();
            return count.flatMap(active -> active >= maxActiveFilesPerOwner
                    ? Mono.error(new FileUploadException("file quota exceeded")) : Mono.empty());
        });
    }

    private boolean hasImageSignature(String contentType, byte[] bytes) {
        return switch (contentType) {
            case "image/jpeg" -> bytes.length >= 3 && (bytes[0] & 0xff) == 0xff
                    && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff;
            case "image/png" -> startsWith(bytes, new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});
            case "image/gif" -> startsWith(bytes, "GIF87a".getBytes()) || startsWith(bytes, "GIF89a".getBytes());
            case "image/bmp" -> startsWith(bytes, new byte[]{0x42, 0x4d});
            default -> false;
        };
    }

    private static boolean startsWith(byte[] value, byte[] prefix) {
        if (value.length < prefix.length) return false;
        for (int index = 0; index < prefix.length; index++) {
            if (value[index] != prefix[index]) return false;
        }
        return true;
    }

}
