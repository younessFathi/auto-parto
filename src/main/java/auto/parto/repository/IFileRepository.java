package auto.parto.repository;

import auto.parto.dto.FileDto;
import auto.parto.model.File;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface IFileRepository {
    Mono<File> save(File file);
    Flux<File> getFilesByPieceId(String pieceId);
    Flux<File> findAllByIds(List<String> imagesId);
}
