package auto.parto.service;

import auto.parto.dto.CreateOfferRequest;
import auto.parto.dto.OfferResponse;
import auto.parto.dto.UpdateOfferStatusRequest;
import auto.parto.persistence.OfferRecord;
import auto.parto.persistence.OfferRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PostgresqlOfferServiceTest {
    private final OfferRepository repository = mock(OfferRepository.class);
    private final PostgresqlOfferService service = new PostgresqlOfferService(provider(repository));
    private final UUID buyer = UUID.randomUUID();
    private final UUID seller = UUID.randomUUID();
    private final UUID target = UUID.randomUUID();
    private final UUID offer = UUID.randomUUID();
    private final OfferRecord record = new OfferRecord(offer, target, null, buyer, seller,
            1000, "MAD", "PENDING", true, 0, null, Instant.now(), Instant.now());

    @Test
    void createsRequestOfferUsingAuthenticatedSeller() {
        when(repository.createForRequest(seller, target, 1000, "MAD", true, 0, null))
                .thenReturn(Mono.just(record));

        OfferResponse response = service.createForRequest(seller, target,
                new CreateOfferRequest(1000L, "MAD", true, 0L, null)).block();

        assertThat(response.publicId()).isEqualTo(offer);
        verify(repository).createForRequest(seller, target, 1000, "MAD", true, 0, null);
    }

    @Test
    void actorFilterHidesOffersOwnedByAnotherUser() {
        when(repository.findByPublicId(offer)).thenReturn(Mono.just(record));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service
                        .findForActor(offer, UUID.randomUUID()).block())
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test
    void updatesStatusWithOptimisticVersion() {
        when(repository.updateStatus(eq(offer), eq(seller), eq("ACCEPTED"), eq(0L), any(Instant.class)))
                .thenReturn(Mono.just(record));

        assertThat(service.updateStatus(offer, seller,
                new UpdateOfferStatusRequest("ACCEPTED", 0L)).block().status()).isEqualTo("PENDING");
    }

    private static ObjectProvider<OfferRepository> provider(OfferRepository repository) {
        return new ObjectProvider<>() {
            @Override public OfferRepository getObject(Object... args) { return repository; }
            @Override public OfferRepository getIfAvailable() { return repository; }
            @Override public OfferRepository getIfUnique() { return repository; }
        };
    }
}
