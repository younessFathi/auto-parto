package auto.parto.repository;

import auto.parto.model.File;
import reactor.core.publisher.Mono;

public interface IFileRepository {
    Mono<File> save(File file);
}
