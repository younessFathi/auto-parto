package auto.parto.service.iservice;

import auto.parto.dto.FileDto;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface IFileService {
    Mono<FileDto> save(FileDto fileDto);
    Flux<FileDto> getFilesByPieceId(String pieceId);
    Flux<FileDto> findAllByIds(List<String> imagesId);
}
