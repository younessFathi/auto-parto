package auto.parto.service;

import auto.parto.dto.FileDto;
import auto.parto.mapper.FileDbMapper;
import auto.parto.model.File;
import auto.parto.repository.IFileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class FileServiceTest {
    @Mock
    private IFileRepository fileRepository;
    @Mock
    private FileDbMapper fileDbMapper;

    @InjectMocks
    private FileService fileService;

    @Test
    void testSave() {
        FileDto fileDto = new FileDto();
        File file = new File();
        when(fileRepository.save(file)).thenReturn(Mono.just(file));
        when(fileDbMapper.toDto(file)).thenReturn(fileDto);
        when(fileDbMapper.toEntity(fileDto)).thenReturn(file);

        StepVerifier.create(fileService.save(fileDto))
                .expectNext(fileDto)
                .verifyComplete();

        verify(fileDbMapper, times(1)).toEntity(fileDto);
        verify(fileRepository, times(1)).save(file);
        verify(fileDbMapper, times(1)).toDto(file);
    }

    @Test
    void testSaveWithError() {
        FileDto fileDto = new FileDto();
        File file = new File();
        when(fileDbMapper.toEntity(fileDto)).thenReturn(file);
        when(fileRepository.save(file)).thenReturn(Mono.error(new RuntimeException("Save failed")));

        StepVerifier.create(fileService.save(fileDto))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException && throwable.getMessage().equals("Save failed"))
                .verify();

        verify(fileDbMapper, times(1)).toEntity(fileDto);
        verify(fileRepository, times(1)).save(file);
    }

    @Test
    void testGetFilesByPieceId() {
        String pieceId = "test-piece-id";
        File file = new File();
        FileDto fileDto = new FileDto();
        when(fileRepository.getFilesByPieceId(pieceId)).thenReturn(Flux.just(file));
        when(fileDbMapper.toDto(file)).thenReturn(fileDto);

        StepVerifier.create(fileService.getFilesByPieceId(pieceId))
                .expectNext(fileDto)
                .verifyComplete();

        verify(fileRepository, times(1)).getFilesByPieceId(pieceId);
        verify(fileDbMapper, times(1)).toDto(file);
    }

    @Test
    void testGetFilesByPieceIdNoFiles() {
        String pieceId = "test-piece-id";
        when(fileRepository.getFilesByPieceId(pieceId)).thenReturn(Flux.empty());

        StepVerifier.create(fileService.getFilesByPieceId(pieceId))
                .verifyComplete();

        verify(fileRepository, times(1)).getFilesByPieceId(pieceId);
        verifyNoInteractions(fileDbMapper);
    }

    @Test
    void testFindAllByIds() {
        List<String> ids = List.of("id1", "id2");
        File file = new File();
        FileDto fileDto = new FileDto();
        when(fileRepository.findAllByIds(ids)).thenReturn(Flux.just(file));
        when(fileDbMapper.toDto(file)).thenReturn(fileDto);

        StepVerifier.create(fileService.findAllByIds(ids))
                .expectNext(fileDto)
                .verifyComplete();

        verify(fileRepository, times(1)).findAllByIds(ids);
        verify(fileDbMapper, times(1)).toDto(file);
    }

    @Test
    void testFindAllByIdsEmptyList() {
        List<String> ids = Collections.emptyList();
        when(fileRepository.findAllByIds(ids)).thenReturn(Flux.empty());

        StepVerifier.create(fileService.findAllByIds(ids))
                .verifyComplete();

        verify(fileRepository, times(1)).findAllByIds(ids);
        verifyNoInteractions(fileDbMapper);
    }

    @Test
    void testFindAllByIdsWithError() {
        List<String> ids = List.of("id1", "id2");
        when(fileRepository.findAllByIds(ids)).thenReturn(Flux.error(new RuntimeException("Find failed")));

        StepVerifier.create(fileService.findAllByIds(ids))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException && throwable.getMessage().equals("Find failed"))
                .verify();

        verify(fileRepository, times(1)).findAllByIds(ids);
    }
}
