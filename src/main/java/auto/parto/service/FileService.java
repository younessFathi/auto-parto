package auto.parto.service;

import auto.parto.dto.FileDto;
import auto.parto.mapper.FileMapper;
import auto.parto.repository.IFileRepository;
import auto.parto.service.iservice.IFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileService implements IFileService {
    private final IFileRepository fileRepository;

    @Override
    public Mono<FileDto> save(FileDto fileDto) {
        return fileRepository.save(FileMapper.toEntity(fileDto))
                            .map(FileMapper::toDto);
    }
}
