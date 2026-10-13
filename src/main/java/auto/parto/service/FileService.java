package auto.parto.service;

import auto.parto.dto.FileDto;
import auto.parto.mapper.FileDbMapper;
import auto.parto.persistence.FileRecord;
import auto.parto.persistence.FileRepository;
import auto.parto.repository.IFileRepository;
import auto.parto.service.iservice.IFileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class FileService implements IFileService {
    private final IFileRepository fileRepository;
    private final FileDbMapper fileDbMapper;
    private final FileRepository postgresqlFileRepository;

    public FileService(IFileRepository fileRepository, FileDbMapper fileDbMapper) {
        this(fileRepository, fileDbMapper, (FileRepository) null);
    }

    @Autowired
    public FileService(IFileRepository fileRepository, FileDbMapper fileDbMapper,
                       ObjectProvider<FileRepository> postgresqlFileRepositoryProvider) {
        this(fileRepository, fileDbMapper, postgresqlFileRepositoryProvider.getIfAvailable());
    }

    public FileService(IFileRepository fileRepository, FileDbMapper fileDbMapper,
                       FileRepository postgresqlFileRepository) {
        this.fileRepository = fileRepository;
        this.fileDbMapper = fileDbMapper;
        this.postgresqlFileRepository = postgresqlFileRepository;
    }

    @Override
    public Mono<FileDto> save(FileDto fileDto) {
        return fileRepository.save(fileDbMapper.toEntity(fileDto))
                            .map(fileDbMapper::toDto);
    }

    @Override
    public Flux<FileDto> getFilesByPieceId(String pieceId) {
        log.info("{} : {}", "Getting files by piece id" , pieceId);
        UUID requestPublicId = parseUuid(pieceId);
        if (postgresqlFileRepository != null && requestPublicId != null) {
            return postgresqlFileRepository.findByRequest(requestPublicId, 100)
                    .map(this::toDto);
        }
        return fileRepository.getFilesByPieceId(pieceId)
                             .map(fileDbMapper::toDto);
    }

    @Override
    public Flux<FileDto> findAllByIds(List<String> imagesId) {
        List<UUID> publicIds = parsePublicIds(imagesId);
        if (postgresqlFileRepository != null && publicIds.size() == imagesId.size() && !publicIds.isEmpty()) {
            return postgresqlFileRepository.findByPublicIds(publicIds, 100)
                    .map(this::toDto);
        }
        return fileRepository.findAllByIds(imagesId)
                .map(fileDbMapper::toDto);
    }

    private static List<UUID> parsePublicIds(List<String> values) {
        if (values == null || values.isEmpty() || values.size() > 100) return List.of();
        try {
            return values.stream().map(UUID::fromString).toList();
        } catch (IllegalArgumentException exception) {
            return List.of();
        }
    }

    private FileDto toDto(FileRecord record) {
        String targetId = record.partRequestPublicId() != null
                ? record.partRequestPublicId().toString()
                : record.partListingPublicId().toString();
        return FileDto.builder()
                .id(record.publicId().toString())
                .originalFilename(record.originalFilename())
                .generatedFilename(record.objectKey())
                .createdAt(record.createdAt() == null ? null
                        : record.createdAt().atZone(java.time.ZoneOffset.UTC).toLocalDateTime())
                .folderName(targetId)
                .pieceId(record.partRequestPublicId() == null ? null : targetId)
                .build();
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
