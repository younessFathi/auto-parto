package auto.parto.repository.mongo_repository;

import auto.parto.model.File;
import auto.parto.repository.IFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
@Slf4j
public class FileRepository implements IFileRepository {
    private final ReactiveMongoTemplate reactiveMongoTemplate;
    @Override
    public Mono<File> save(File file) {
        return reactiveMongoTemplate.save(file);
    }
}
