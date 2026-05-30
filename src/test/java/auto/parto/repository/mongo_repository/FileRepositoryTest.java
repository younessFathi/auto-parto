package auto.parto.repository.mongo_repository;

import auto.parto.model.File;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileRepositoryTest {

    @Mock
    private ReactiveMongoTemplate reactiveMongoTemplate;

    private FileRepository fileRepository;

    @BeforeEach
    void setUp() {
        fileRepository = new FileRepository(reactiveMongoTemplate);
    }

    @Test
    void getFilesByPieceIdQueriesByPieceIdField() {
        when(reactiveMongoTemplate.find(org.mockito.ArgumentMatchers.any(Query.class), eq(File.class)))
                .thenReturn(Flux.empty());

        StepVerifier.create(fileRepository.getFilesByPieceId("piece-1"))
                .verifyComplete();

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(reactiveMongoTemplate).find(queryCaptor.capture(), eq(File.class));

        assertThat(queryCaptor.getValue().getQueryObject().get("pieceId")).isEqualTo("piece-1");
    }

    @Test
    void findAllByIdsQueriesByIdField() {
        when(reactiveMongoTemplate.find(org.mockito.ArgumentMatchers.any(Query.class), eq(File.class)))
                .thenReturn(Flux.empty());

        StepVerifier.create(fileRepository.findAllByIds(java.util.List.of("img-1", "img-2")))
                .verifyComplete();

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(reactiveMongoTemplate).find(queryCaptor.capture(), eq(File.class));

        assertThat(queryCaptor.getValue().getQueryObject().get("_id")).isNotNull();
    }
}
