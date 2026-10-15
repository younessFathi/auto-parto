package auto.parto.service;

import auto.parto.dto.PartRequestCreateRequest;
import auto.parto.dto.PartRequestPageResponse;
import auto.parto.persistence.PartRequestRecord;
import auto.parto.persistence.PartRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PartRequestServiceTest {

    @Mock
    private PartRequestRepository repository;

    private PartRequestService service;

    @BeforeEach
    void setUp() {
        service = new PartRequestService(repository);
    }

    @Test
    void createsRequestForAuthenticatedBuyer() {
        UUID buyerId = UUID.randomUUID();
        PartRequestRecord created = request(buyerId, "OPEN");
        when(repository.create(eq(buyerId), eq("Brake pads"), any(), any(), any(), any(), any(), any()))
                .thenReturn(Mono.just(created));

        StepVerifier.create(service.create(buyerId,
                        new PartRequestCreateRequest("Brake pads", "Front axle", "NEW", "BRAKES",
                                "Toyota", "Corolla", 2020)))
                .assertNext(response -> {
                    assertThat(response.publicId()).isEqualTo(created.publicId());
                    assertThat(response.buyerUserPublicId()).isEqualTo(buyerId);
                    assertThat(response.status()).isEqualTo("OPEN");
                })
                .verifyComplete();
    }

    @Test
    void mapsMissingBuyerDuringCreateToNotFound() {
        UUID buyerId = UUID.randomUUID();
        when(repository.create(eq(buyerId), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.create(buyerId,
                        new PartRequestCreateRequest("Brake pads", null, null, null, null, null, null)))
                .expectErrorSatisfies(error -> assertThat(error).isInstanceOf(auto.parto.exception.EntityNotFoundException.class))
                .verify();
    }

    @Test
    void fetchesOneExtraRecordAndReturnsNextCursor() {
        UUID buyerId = UUID.randomUUID();
        PartRequestRecord first = request(buyerId, "OPEN");
        PartRequestRecord second = request(buyerId, "OPEN");
        PartRequestRecord extra = request(buyerId, "OPEN");
        when(repository.findByBuyerUserPublicId(eq(buyerId), isNull(), isNull(), eq(3)))
                .thenReturn(reactor.core.publisher.Flux.just(first, second, extra));

        StepVerifier.create(service.findMine(buyerId, null, 2))
                .assertNext(page -> {
                    assertThat(page.items()).hasSize(2);
                    assertThat(page.items().get(0).publicId()).isEqualTo(first.publicId());
                    assertThat(page.nextCursor()).isNotBlank();
                })
                .verifyComplete();
    }

    @Test
    void rejectsTransitionByAnotherBuyer() {
        UUID ownerId = UUID.randomUUID();
        UUID otherBuyerId = UUID.randomUUID();
        PartRequestRecord existing = request(ownerId, "OPEN");
        when(repository.findByPublicId(existing.publicId())).thenReturn(Mono.just(existing));

        StepVerifier.create(service.close(otherBuyerId, existing.publicId()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ResponseStatusException.class);
                    assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(403);
                })
                .verify();
    }

    @Test
    void rejectsTransitionFromClosedRequestAsConflict() {
        UUID ownerId = UUID.randomUUID();
        PartRequestRecord existing = request(ownerId, "CLOSED");
        when(repository.findByPublicId(existing.publicId())).thenReturn(Mono.just(existing));

        StepVerifier.create(service.cancel(ownerId, existing.publicId()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ResponseStatusException.class);
                    assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(409);
                })
                .verify();
    }

    @Test
    void closesOwnedOpenRequest() {
        UUID ownerId = UUID.randomUUID();
        PartRequestRecord existing = request(ownerId, "OPEN");
        PartRequestRecord closed = request(ownerId, "CLOSED");
        when(repository.findByPublicId(existing.publicId())).thenReturn(Mono.just(existing));
        when(repository.transition(existing.publicId(), ownerId, "CLOSED")).thenReturn(Mono.just(closed));

        StepVerifier.create(service.close(ownerId, existing.publicId()))
                .assertNext(response -> assertThat(response.status()).isEqualTo("CLOSED"))
                .verifyComplete();

        verify(repository).transition(existing.publicId(), ownerId, "CLOSED");
    }

    private static PartRequestRecord request(UUID buyerId, String status) {
        Instant now = Instant.now();
        return new PartRequestRecord(UUID.randomUUID(), buyerId, "Brake pads", "Front axle", "NEW",
                "BRAKES", "Toyota", "Corolla", 2020, status, now, now, 0);
    }
}
