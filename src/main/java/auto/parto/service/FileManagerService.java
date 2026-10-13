package auto.parto.service;

import auto.parto.dto.FileDto;
import auto.parto.exception.FileUploadException;
import auto.parto.persistence.FileRepository;
import auto.parto.service.iservice.IFileManagerService;
import auto.parto.service.iservice.IFileService;
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

    public FileManagerService(@Value("${auto.parto.images.uploads}") String uploadsDirectory , IFileService fileService) {
        this(uploadsDirectory, fileService, (FileRepository) null);
    }

    public FileManagerService(@Value("${auto.parto.images.uploads}") String uploadsDirectory,
                              IFileService fileService, FileRepository postgresqlFileRepository) {
        this.uploadsDirectory = uploadsDirectory;
        this.fileService = fileService;
        this.postgresqlFileRepository = postgresqlFileRepository;
    }

    @Autowired
    public FileManagerService(@Value("${auto.parto.images.uploads}") String uploadsDirectory,
                              IFileService fileService,
                              ObjectProvider<FileRepository> postgresqlFileRepositoryProvider) {
        this(uploadsDirectory, fileService, postgresqlFileRepositoryProvider.getIfAvailable());
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
        if (postgresqlFileRepository == null) {
            return Mono.error(new IllegalStateException("PostgreSQL file persistence is not enabled"));
        }
        if (ownerUserPublicId == null || requestPublicId == null) {
            return Mono.error(new IllegalArgumentException("owner and request IDs are required"));
        }
        return writeImage(image, requestPublicId.toString())
                .flatMap(stored -> postgresqlFileRepository.createForRequest(ownerUserPublicId, requestPublicId,
                                requestPublicId + "/" + stored.generatedFilename(), image.filename(),
                                contentType(image), stored.byteSize(), stored.checksum())
                        .switchIfEmpty(Mono.error(new IllegalArgumentException("request is not owned by the authenticated user")))
                        .map(record -> record.publicId().toString())
                        .onErrorResume(error -> deleteStagedFile(stored.destinationFile())
                                .then(Mono.error(error))));
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
