package auto.parto.repository.mongo_repository;

import auto.parto.config.BaseIntegrationTest;
import auto.parto.enums.Status;
import auto.parto.model.PieceOffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PieceOfferRepositoryIT extends BaseIntegrationTest {

    @Autowired
    private PieceOfferRepository pieceOfferRepository;

    @Autowired
    private ReactiveMongoTemplate reactiveMongoTemplate;

    @AfterEach
    void cleanUp() {
        reactiveMongoTemplate.dropCollection(PieceOffer.class).block();
    }

    @Test
    void shouldSaveAndRetrieveOffer() {
        PieceOffer offer = PieceOffer.builder()
                .pieceId("piece-001")
                .customerId("cust-001")
                .name("Alternator")
                .priceMinor(12000L)
                .isAvailable(true)
                .status(Status.PENDING)
                .imagesId(List.of("img-1", "img-2"))
                .build();

        StepVerifier.create(pieceOfferRepository.save(offer))
                .assertNext(saved -> {
                    assertThat(saved.getId()).isNotNull();
                    assertThat(saved.getName()).isEqualTo("Alternator");
                    assertThat(saved.getPriceMinor()).isEqualTo(12000L);
                    assertThat(saved.getImagesId()).containsExactly("img-1", "img-2");
                })
                .verifyComplete();
    }

    @Test
    void shouldUpdateImagesOfExistingOffer() {
        // Given: a persisted offer with one image
        PieceOffer offer = PieceOffer.builder()
                .pieceId("piece-002")
                .customerId("cust-002")
                .name("Brake Pad")
                .imagesId(List.of("old-img"))
                .build();

        String savedId = pieceOfferRepository.save(offer).block().getId();

        // When: we update with new images
        List<String> newImages = List.of("new-img-1", "new-img-2", "new-img-3");

        StepVerifier.create(pieceOfferRepository.update(savedId, newImages))
                .assertNext(updated -> {
                    assertThat(updated.getId()).isEqualTo(savedId);
                    assertThat(updated.getImagesId()).containsExactlyElementsOf(newImages);
                    // Other fields must remain unchanged
                    assertThat(updated.getName()).isEqualTo("Brake Pad");
                    assertThat(updated.getCustomerId()).isEqualTo("cust-002");
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnEmptyWhenUpdatingNonExistentOffer() {
        // Updating a non-existent ID should return empty (findAndModify returns null → Mono.empty)
        StepVerifier.create(pieceOfferRepository.update("non-existent-id", List.of("img-x")))
                .verifyComplete();
    }

    @Test
    void shouldPersistOfferWithoutImages() {
        PieceOffer offer = PieceOffer.builder()
                .pieceId("piece-003")
                .customerId("cust-003")
                .name("Air Filter")
                .isAvailable(true)
                .build();

        StepVerifier.create(pieceOfferRepository.save(offer))
                .assertNext(saved -> {
                    assertThat(saved.getId()).isNotNull();
                    assertThat(saved.getImagesId()).isNullOrEmpty();
                })
                .verifyComplete();
    }
}
