package auto.parto.service;

import auto.parto.dto.FileDto;
import auto.parto.exception.FileUploadException;
import auto.parto.service.iservice.IFileManagerService;
import auto.parto.service.iservice.IFileService;
import auto.parto.utils.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.apache.commons.io.FilenameUtils.getExtension;

@Service
@Slf4j
public class FileManagerService implements IFileManagerService {
    @Value("${auto.parto.images.uploads}")
    private String uploadsDirectory;
    private IFileService fileService;

    public FileManagerService(@Value("${auto.parto.images.uploads}") String uploadsDirectory , IFileService fileService) {
        this.uploadsDirectory = uploadsDirectory;
        this.fileService = fileService;
    }

    @Override
    public Mono<String> save(FilePart image , String pieceId) {
        log.info("{} : {}" , "Saving File",  image.filename());
        Path rootLocation = Paths.get(uploadsDirectory +"/"+pieceId);
        createUploadDirectory(rootLocation);
        String fileExtension = getExtension(image.filename());
        if (!isAllowedExtension(fileExtension)) {
            return Mono.error(new FileUploadException(Constants.FileUpload.INVALID_FILE_TYPE_ONLY_IMAGE_FILES_ARE_ALLOWED));
        }
        String generatedFileName = UUID.randomUUID() + "." + fileExtension;

        Path destinationFile = rootLocation.resolve(generatedFileName).normalize();
        return DataBufferUtils.write(
                    image.content(), // Content is a Flux<DataBuffer>
                    destinationFile,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING
                ).doOnTerminate(() -> log.info("{} : {}", Constants.FileUpload.FILE_SAVED_TO_DISK,  destinationFile))
                .then(Mono.fromCallable(() -> {
                // Create FileDto
                    return FileDto.builder()
                                .createdAt(LocalDateTime.now())
                                .generatedFilename(generatedFileName)
                                .id(UUID.randomUUID().toString())
                                .originalFilename(image.filename())
                                .folderName(pieceId)
                                .build();
                }))
                .flatMap(fileService::save)
                .map(FileDto::getId)
                // Persist metadata in the database
                .doOnSuccess(file -> log.info("{} : {}", Constants.FileUpload.FILE_SAVED, file));
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
