package auto.parto.service;

import auto.parto.dto.FileDto;
import auto.parto.mapper.FileDbMapper;
import auto.parto.repository.IFileRepository;
import auto.parto.service.iservice.IFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileService implements IFileService {
    private final IFileRepository fileRepository;
    private final FileDbMapper fileDbMapper;

    @Override
    public Mono<FileDto> save(FileDto fileDto) {
        return fileRepository.save(fileDbMapper.toEntity(fileDto))
                            .map(fileDbMapper::toDto);
    }

    @Override
    public Flux<FileDto> getFilesByPieceId(String pieceId) {
        log.info("{} : {}", "Getting files by piece id" , pieceId);
        return fileRepository.getFilesByPieceId(pieceId)
                             .map(fileDbMapper::toDto);
    }

    @Override
    public Flux<FileDto> findAllByIds(List<String> imagesId) {
        return fileRepository.findAllByIds(imagesId)
                .map(fileDbMapper::toDto);
    }
}
