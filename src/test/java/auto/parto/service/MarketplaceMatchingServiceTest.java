package auto.parto.service;

import auto.parto.enums.Category;
import auto.parto.enums.Condition;
import auto.parto.persistence.PartListingRecord;
import auto.parto.persistence.PartListingRepository;
import auto.parto.persistence.PartRequestRecord;
import auto.parto.persistence.PartRequestRepository;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MarketplaceMatchingServiceTest {
    private final PartRequestRepository requests = mock(PartRequestRepository.class);
    private final PartListingRepository listings = mock(PartListingRepository.class);
    private final MarketplaceMatchingService service = new MarketplaceMatchingService(requests, listings);
    private final UUID buyer = UUID.randomUUID();
    private final UUID requestId = UUID.randomUUID();

    @Test
    void returnsBoundedMatchesForOwnedOpenRequest() {
        PartRequestRecord request = request("OPEN", buyer);
        PartListingRecord listing = listing();
        when(requests.findByPublicId(requestId)).thenReturn(reactor.core.publisher.Mono.just(request));
        when(listings.findMatches(request, 2)).thenReturn(Flux.just(listing));

        StepVerifier.create(service.findMatches(buyer, requestId, 2))
                .assertNext(response -> {
                    assertThat(response.items()).hasSize(1);
                    assertThat(response.items().getFirst().publicId()).isEqualTo(listing.publicId());
                    assertThat(response.limit()).isEqualTo(2);
                })
                .verifyComplete();
        verify(listings).findMatches(eq(request), eq(2));
    }

    @Test
    void rejectsAnotherBuyerAndDoesNotQueryListings() {
        PartRequestRecord request = request("OPEN", buyer);
        when(requests.findByPublicId(requestId)).thenReturn(reactor.core.publisher.Mono.just(request));

        StepVerifier.create(service.findMatches(UUID.randomUUID(), requestId, 20))
                .expectErrorMatches(error -> error.getMessage().contains("does not own"))
                .verify();
        verifyNoInteractions(listings);
    }

    @Test
    void rejectsClosedRequest() {
        when(requests.findByPublicId(requestId)).thenReturn(reactor.core.publisher.Mono.just(request("CLOSED", buyer)));

        StepVerifier.create(service.findMatches(buyer, requestId, 20))
                .expectErrorMatches(error -> error.getMessage().contains("Only open"))
                .verify();
        verifyNoInteractions(listings);
    }

    private PartRequestRecord request(String status, UUID owner) {
        return new PartRequestRecord(requestId, owner, "Brake pads", "Front", Condition.NEW,
                Category.CAR, status, 0, Instant.now(), Instant.now(), "Toyota", "Corolla", 2020, 0);
    }

    private PartListingRecord listing() {
        return new PartListingRecord(UUID.randomUUID(), UUID.randomUUID(), "Brake pads", "Front",
                "Toyota", "Corolla", 2020, Condition.NEW, Category.CAR, 12000, "MAD", "PUBLISHED",
                "PUBLISHED", "NOT_READY", "NOT_STORED", 0, Instant.now(), Instant.now(), 0);
    }
}
