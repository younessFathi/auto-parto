package auto.parto.repository.mongo_repository;

import auto.parto.model.Piece;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PieceRepositoryTest {

    @Mock
    private ReactiveMongoTemplate reactiveMongoTemplate;

    private PieceRepository pieceRepository;

    @BeforeEach
    void setUp() {
        pieceRepository = new PieceRepository(reactiveMongoTemplate);
    }

    @Test
    void getAllPiecesFiltersByCustomerIdAndAppliesPagination() {
        when(reactiveMongoTemplate.find(org.mockito.ArgumentMatchers.any(Query.class), eq(Piece.class)))
                .thenReturn(Flux.empty());

        StepVerifier.create(pieceRepository.getAllPieces("customer-1", 2, 5))
                .verifyComplete();

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(reactiveMongoTemplate).find(queryCaptor.capture(), eq(Piece.class));

        Query query = queryCaptor.getValue();
        Document queryObject = query.getQueryObject();

        assertThat(queryObject.get("customerId")).isEqualTo("customer-1");
        assertThat(query.getSkip()).isEqualTo(10);
        assertThat(query.getLimit()).isEqualTo(5);
        assertThat(query.getSortObject().toJson()).contains("createAt");
    }

    @Test
    void getTotalPiecesCountsByCustomerIdFromPieceCollection() {
        when(reactiveMongoTemplate.count(org.mockito.ArgumentMatchers.any(Query.class), eq(Piece.class)))
                .thenReturn(Mono.just(3L));

        StepVerifier.create(pieceRepository.getTotalPieces("customer-2"))
                .expectNext(3L)
                .verifyComplete();

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(reactiveMongoTemplate).count(queryCaptor.capture(), eq(Piece.class));
        assertThat(queryCaptor.getValue().getQueryObject().get("customerId")).isEqualTo("customer-2");
    }

    @Test
    void updatePersistsUpdatedImages() {
        when(reactiveMongoTemplate.findAndModify(
                org.mockito.ArgumentMatchers.any(Query.class),
                org.mockito.ArgumentMatchers.any(Update.class),
                org.mockito.ArgumentMatchers.any(FindAndModifyOptions.class),
                eq(Piece.class))
        ).thenReturn(Mono.just(Piece.builder().id("piece-1").imagesId(List.of("img-1", "img-2")).build()));

        StepVerifier.create(pieceRepository.update("piece-1", List.of("img-1", "img-2")))
                .assertNext(updated -> assertThat(updated.getImagesId()).containsExactly("img-1", "img-2"))
                .verifyComplete();

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        ArgumentCaptor<FindAndModifyOptions> optionsCaptor = ArgumentCaptor.forClass(FindAndModifyOptions.class);
        verify(reactiveMongoTemplate).findAndModify(
                queryCaptor.capture(),
                updateCaptor.capture(),
                optionsCaptor.capture(),
                eq(Piece.class)
        );
        assertThat(queryCaptor.getValue().getQueryObject().get("_id")).isEqualTo("piece-1");
        assertThat(updateCaptor.getValue().getUpdateObject().toJson()).contains("imagesId");
        assertThat(optionsCaptor.getValue().isReturnNew()).isTrue();
    }
}
