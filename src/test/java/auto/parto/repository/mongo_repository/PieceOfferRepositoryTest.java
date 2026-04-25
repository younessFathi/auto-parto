package auto.parto.repository.mongo_repository;

import auto.parto.model.PieceOffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PieceOfferRepositoryTest {

    @Mock
    private ReactiveMongoTemplate reactiveMongoTemplate;

    private PieceOfferRepository pieceOfferRepository;

    @BeforeEach
    void setUp() {
        pieceOfferRepository = new PieceOfferRepository(reactiveMongoTemplate);
    }

    @Test
    void updatePersistsUpdatedImages() {
        PieceOffer offer = PieceOffer.builder().id("offer-1").imagesId(List.of("old")).build();
        when(reactiveMongoTemplate.findById("offer-1", PieceOffer.class)).thenReturn(Mono.just(offer));
        when(reactiveMongoTemplate.save(org.mockito.ArgumentMatchers.any(PieceOffer.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(pieceOfferRepository.update("offer-1", List.of("img-a", "img-b")))
                .assertNext(updated -> assertThat(updated.getImagesId()).containsExactly("img-a", "img-b"))
                .verifyComplete();

        verify(reactiveMongoTemplate).save(org.mockito.ArgumentMatchers.any(PieceOffer.class));
    }
}
