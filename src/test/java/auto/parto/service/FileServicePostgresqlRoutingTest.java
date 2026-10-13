package auto.parto.service;

import auto.parto.dto.FileDto;
import auto.parto.mapper.FileDbMapper;
import auto.parto.persistence.FileRecord;
import auto.parto.persistence.FileRepository;
import auto.parto.repository.IFileRepository;
import auto.parto.model.File;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class FileServicePostgresqlRoutingTest {

    private final IFileRepository mongoRepository = mock(IFileRepository.class);
    private final FileDbMapper mapper = mock(FileDbMapper.class);
    private final FileRepository postgresqlRepository = mock(FileRepository.class);
    private final FileService service = new FileService(mongoRepository, mapper, postgresqlRepository);

    @Test
    void canonicalRequestIdReadsBoundedPostgresqlMetadata() {
        UUID requestId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        when(postgresqlRepository.findByRequest(requestId, 100)).thenReturn(Flux.just(
                new FileRecord(fileId, UUID.randomUUID(), requestId, null, "objects/photo.jpg",
                        "photo.jpg", "image/jpeg", 12L, "checksum", "ACTIVE", Instant.now(), Instant.now())));

        FileDto result = service.getFilesByPieceId(requestId.toString()).blockFirst();

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(fileId.toString());
        assertThat(result.getPieceId()).isEqualTo(requestId.toString());
        assertThat(result.getGeneratedFilename()).isEqualTo("objects/photo.jpg");
        verify(postgresqlRepository).findByRequest(requestId, 100);
        verifyNoInteractions(mongoRepository, mapper);
    }

    @Test
    void legacyIdentifierFallsBackToMongoRepository() {
        File file = File.builder().id("file-1").pieceId("piece-1").build();
        FileDto dto = FileDto.builder().id("file-1").pieceId("piece-1").build();
        when(mongoRepository.getFilesByPieceId("piece-1")).thenReturn(Flux.just(file));
        when(mapper.toDto(file)).thenReturn(dto);

        FileDto result = service.getFilesByPieceId("piece-1").blockFirst();

        assertThat(result).isSameAs(dto);
        verify(mongoRepository).getFilesByPieceId("piece-1");
        verifyNoInteractions(postgresqlRepository);
    }

    @Test
    void canonicalFileBatchReadsBoundedPostgresqlMetadata() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(postgresqlRepository.findByPublicIds(List.of(first, second), 100)).thenReturn(Flux.just(
                new FileRecord(first, UUID.randomUUID(), UUID.randomUUID(), null, "objects/one.jpg",
                        "one.jpg", "image/jpeg", 1L, "one", "ACTIVE", Instant.now(), Instant.now()),
                new FileRecord(second, UUID.randomUUID(), UUID.randomUUID(), null, "objects/two.jpg",
                        "two.jpg", "image/jpeg", 2L, "two", "ACTIVE", Instant.now(), Instant.now())));

        List<FileDto> result = service.findAllByIds(List.of(first.toString(), second.toString())).collectList().block();

        assertThat(result).hasSize(2).extracting(FileDto::getId).containsExactly(first.toString(), second.toString());
        verify(postgresqlRepository).findByPublicIds(List.of(first, second), 100);
        verifyNoInteractions(mongoRepository, mapper);
    }
}
