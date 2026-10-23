package auto.parto.service;

import auto.parto.dto.FileDto;
import auto.parto.persistence.FileRecord;
import auto.parto.persistence.FileRepository;
import auto.parto.service.iservice.IFileService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@Service
public class FileService implements IFileService {
    private final FileRepository repository;
    public FileService(FileRepository repository) { this.repository = repository; }
    @Override public Mono<FileDto> save(FileDto fileDto) { return Mono.error(new UnsupportedOperationException("file metadata is created through the upload lifecycle")); }
    @Override public Flux<FileDto> getFilesByPieceId(String pieceId) { return repository.findByRequest(requireUuid(pieceId), 100).map(FileService::toDto); }
    @Override public Flux<FileDto> findAllByIds(List<String> imageIds) { if (imageIds == null || imageIds.isEmpty() || imageIds.size() > 100) return Flux.error(new IllegalArgumentException("at least one and at most 100 file IDs are required")); try { return repository.findByPublicIds(imageIds.stream().map(UUID::fromString).toList(), 100).map(FileService::toDto); } catch (IllegalArgumentException e) { return Flux.error(new IllegalArgumentException("file IDs must be UUIDs", e)); } }
    private static UUID requireUuid(String value) { try { return UUID.fromString(value); } catch (RuntimeException e) { throw new IllegalArgumentException("canonical file target ID must be a UUID", e); } }
    private static FileDto toDto(FileRecord record) { String targetId = record.partRequestPublicId() != null ? record.partRequestPublicId().toString() : record.partListingPublicId().toString(); return FileDto.builder().id(record.publicId().toString()).originalFilename(record.originalFilename()).generatedFilename(record.objectKey()).createdAt(record.createdAt() == null ? null : record.createdAt().atZone(java.time.ZoneOffset.UTC).toLocalDateTime()).folderName(targetId).pieceId(record.partRequestPublicId() == null ? null : targetId).build(); }
}
