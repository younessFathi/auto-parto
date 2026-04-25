package auto.parto.repository.mongo_repository;

import auto.parto.config.BaseIntegrationTest;
import auto.parto.model.Piece;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PieceRepositoryIT extends BaseIntegrationTest {

    @Autowired
    private PieceRepository pieceRepository;

    @Autowired
    private ReactiveMongoTemplate reactiveMongoTemplate;

    @AfterEach
    void cleanUp() {
        reactiveMongoTemplate.dropCollection(Piece.class).block();
    }

    @Test
    void shouldSaveAndFindPiece() {
        Piece newPiece = Piece.builder()
                .customerId("cust-123")
                .createAt(LocalDateTime.now())
                .imagesId(List.of("img-1"))
                .build();

        StepVerifier.create(pieceRepository.save(newPiece))
                .assertNext(savedPiece -> {
                    assertThat(savedPiece.getId()).isNotNull();
                    assertThat(savedPiece.getCustomerId()).isEqualTo("cust-123");
                })
                .verifyComplete();

        StepVerifier.create(pieceRepository.getTotalPieces("cust-123"))
                .expectNext(1L)
                .verifyComplete();
    }

    @Test
    void shouldGetAllPiecesWithPagination() {
        Piece piece1 = Piece.builder().customerId("cust-456").createAt(LocalDateTime.now().minusDays(1)).build();
        Piece piece2 = Piece.builder().customerId("cust-456").createAt(LocalDateTime.now()).build();
        Piece piece3 = Piece.builder().customerId("cust-456").createAt(LocalDateTime.now().plusDays(1)).build();
        
        reactiveMongoTemplate.insertAll(List.of(piece1, piece2, piece3)).blockLast();

        // Get first page, size 2 (should return the newest ones first because of DESC sort on createAt)
        StepVerifier.create(pieceRepository.getAllPieces("cust-456", 0, 2))
                .assertNext(p -> assertThat(p.getCreateAt()).isEqualTo(piece3.getCreateAt()))
                .assertNext(p -> assertThat(p.getCreateAt()).isEqualTo(piece2.getCreateAt()))
                .verifyComplete();
    }
}
