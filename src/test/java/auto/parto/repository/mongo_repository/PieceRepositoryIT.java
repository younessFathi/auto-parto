package auto.parto.repository.mongo_repository;

import auto.parto.config.BaseIntegrationTest;
import auto.parto.model.Piece;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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
                .createAt(LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS))
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
        Piece piece1 = Piece.builder().customerId("cust-456").createAt(LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.MILLIS)).build();
        Piece piece2 = Piece.builder().customerId("cust-456").createAt(LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS)).build();
        Piece piece3 = Piece.builder().customerId("cust-456").createAt(LocalDateTime.now().plusDays(1).truncatedTo(ChronoUnit.MILLIS)).build();
        
        reactiveMongoTemplate.insertAll(List.of(piece1, piece2, piece3)).blockLast();

        // Page 0, size 2: newest first
        StepVerifier.create(pieceRepository.getAllPieces("cust-456", 0, 2))
                .assertNext(p -> assertThat(p.getCreateAt()).isEqualTo(piece3.getCreateAt()))
                .assertNext(p -> assertThat(p.getCreateAt()).isEqualTo(piece2.getCreateAt()))
                .verifyComplete();
    }

    @Test
    void shouldReturnSecondPage() {
        Piece piece1 = Piece.builder().customerId("cust-789").createAt(LocalDateTime.now().minusDays(2).truncatedTo(ChronoUnit.MILLIS)).build();
        Piece piece2 = Piece.builder().customerId("cust-789").createAt(LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.MILLIS)).build();
        Piece piece3 = Piece.builder().customerId("cust-789").createAt(LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS)).build();

        reactiveMongoTemplate.insertAll(List.of(piece1, piece2, piece3)).blockLast();

        // Page 1, size 2: should return only the oldest piece
        StepVerifier.create(pieceRepository.getAllPieces("cust-789", 1, 2))
                .assertNext(p -> assertThat(p.getCreateAt()).isEqualTo(piece1.getCreateAt()))
                .verifyComplete();
    }

    @Test
    void shouldReturnZeroCountForUnknownCustomer() {
        StepVerifier.create(pieceRepository.getTotalPieces("non-existent-customer"))
                .expectNext(0L)
                .verifyComplete();
    }

    @Test
    void shouldReturnEmptyFluxWhenNoPiecesExist() {
        StepVerifier.create(pieceRepository.getAllPieces("ghost-customer", 0, 10))
                .verifyComplete();
    }

    @Test
    void shouldNotReturnPiecesFromOtherCustomers() {
        Piece myPiece = Piece.builder().customerId("cust-A").createAt(LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS)).build();
        Piece otherPiece = Piece.builder().customerId("cust-B").createAt(LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS)).build();

        reactiveMongoTemplate.insertAll(List.of(myPiece, otherPiece)).blockLast();

        StepVerifier.create(pieceRepository.getAllPieces("cust-A", 0, 10))
                .assertNext(p -> assertThat(p.getCustomerId()).isEqualTo("cust-A"))
                .verifyComplete();

        StepVerifier.create(pieceRepository.getTotalPieces("cust-A"))
                .expectNext(1L)
                .verifyComplete();
    }
}

