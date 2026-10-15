package auto.parto.service;

import auto.parto.dto.PartListingCreateRequest;
import auto.parto.persistence.PartListingRecord;
import auto.parto.persistence.PartListingRepository;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PartListingServiceTest {
    private final PartListingRepository repository = mock(PartListingRepository.class);
    private final PartListingService service = new PartListingService(repository);
    private final UUID seller = UUID.randomUUID();
    private final UUID listingId = UUID.randomUUID();

    @Test
    void createsSellerOwnedListingWithSeparateStatuses() {
        when(repository.create(eq(seller), any(), any(), any(), any(), any(), any(), any(), anyLong(), any()))
                .thenReturn(Mono.just(record("DRAFT", seller)));

        StepVerifier.create(service.create(seller, new PartListingCreateRequest(
                        "Brake pad", "Front axle", "NEW", "CAR", "Toyota", "Corolla", 2020, 12500, "MAD")))
                .assertNext(response -> {
                    assertThat(response.sellerUserPublicId()).isEqualTo(seller);
                    assertThat(response.businessStatus()).isEqualTo("DRAFT");
                    assertThat(response.deliveryStatus()).isEqualTo("NOT_READY");
                    assertThat(response.storageStatus()).isEqualTo("NOT_STORED");
                }).verifyComplete();
    }

    @Test
    void rejectsInvalidBusinessTransition() {
        when(repository.findByPublicId(listingId)).thenReturn(Mono.just(record("DRAFT", seller)));

        StepVerifier.create(service.transition(seller, listingId, "RESERVED"))
                .expectErrorMatches(error -> error.getMessage().contains("cannot transition"))
                .verify();
        verify(repository, never()).updateStatus(any(), any(), any(), anyLong(), any());
    }

    private PartListingRecord record(String status, UUID owner) {
        return new PartListingRecord(listingId, owner, "Brake pad", "Front axle",
                auto.parto.enums.Condition.NEW, auto.parto.enums.Category.CAR,
                12500, "MAD", status, 0, java.time.Instant.now(), java.time.Instant.now());
    }
}
