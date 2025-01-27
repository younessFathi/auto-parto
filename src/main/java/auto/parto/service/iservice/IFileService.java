package auto.parto.service.iservice;

import auto.parto.dto.FileDto;
import reactor.core.publisher.Mono;

public interface IFileService {
    Mono<FileDto> save(FileDto fileDto);
}
