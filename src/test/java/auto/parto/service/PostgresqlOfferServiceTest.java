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
            1000, "MAD", "PENDING", true, 0, null, Instant.now(), Instant.now(), 0, null);

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
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("does not own");
    }

    @Test
    void updatesStatusWithOptimisticVersion() {
        when(repository.findByPublicId(offer)).thenReturn(Mono.just(record));
        when(repository.updateStatus(eq(offer), eq(buyer), eq("ACCEPTED"), eq(0L), any(Instant.class)))
                .thenReturn(Mono.just(record));

        assertThat(service.updateStatus(offer, buyer,
                new UpdateOfferStatusRequest("ACCEPTED", 0L)).block().status()).isEqualTo("PENDING");
    }

    @Test
    void rejectsCreatorTryingToAcceptRequestOffer() {
        when(repository.findByPublicId(offer)).thenReturn(Mono.just(record));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.updateStatus(offer, seller,
                        new UpdateOfferStatusRequest("ACCEPTED", 0L)).block())
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("Only the offer recipient");
        verify(repository, never()).updateStatus(any(), any(), any(), anyLong(), any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "true,true,ACCEPTED,true", "true,true,REJECTED,true", "true,true,CANCELLED,true",
        "true,false,ACCEPTED,false", "true,false,REJECTED,false", "true,false,CANCELLED,true",
        "false,true,ACCEPTED,false", "false,true,REJECTED,false", "false,true,CANCELLED,true",
        "false,false,ACCEPTED,true", "false,false,REJECTED,true", "false,false,CANCELLED,true"
    })
    void authorityMatrix(boolean requestOrigin, boolean buyerActor, String targetStatus, boolean permitted) {
        OfferRecord persisted = new OfferRecord(offer, requestOrigin ? target : null,
                requestOrigin ? null : target, buyer, seller, 1000, "MAD", "PENDING", true,
                0, null, Instant.now(), Instant.now(), 7, null);
        UUID actor = buyerActor ? buyer : seller;
        when(repository.findByPublicId(offer)).thenReturn(Mono.just(persisted));
        if (permitted) {
            when(repository.updateStatus(eq(offer), eq(actor), eq(targetStatus), eq(7L), any()))
                    .thenReturn(Mono.just(persisted));
            assertThat(service.updateStatus(offer, actor,
                    new UpdateOfferStatusRequest(targetStatus, 7L)).block().version()).isEqualTo(7);
        } else {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.updateStatus(offer, actor,
                    new UpdateOfferStatusRequest(targetStatus, 7L)).block())
                    .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                    .hasMessageContaining("403");
            verify(repository, never()).updateStatus(any(), any(), any(), anyLong(), any());
        }
    }

    @Test
    void unrelatedActorIncludingAdministratorHasNoOverrideAndTerminalCallsConflict() {
        when(repository.findByPublicId(offer)).thenReturn(Mono.just(record));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.updateStatus(offer, UUID.randomUUID(),
                new UpdateOfferStatusRequest("ACCEPTED", 0L)).block()).hasMessageContaining("403");
        OfferRecord terminal = new OfferRecord(offer, target, null, buyer, seller, 1000, "MAD",
                "REJECTED", true, 0, null, Instant.now(), Instant.now(), 1, null);
        when(repository.findByPublicId(offer)).thenReturn(Mono.just(terminal));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.updateStatus(offer, buyer,
                new UpdateOfferStatusRequest("REJECTED", 0L)).block()).hasMessageContaining("409");
        verify(repository, never()).updateStatus(any(), any(), any(), anyLong(), any());
    }

    @Test
    void acceptedRetryDelegatesReceiptValidationWithoutAllowingCreatorReplay() {
        UUID receipt = UUID.randomUUID();
        OfferRecord accepted = new OfferRecord(offer, target, null, buyer, seller, 1000, "MAD",
                "ACCEPTED", true, 0, null, Instant.now(), Instant.now(), 1, receipt);
        when(repository.findByPublicId(offer)).thenReturn(Mono.just(accepted));
        when(repository.updateStatus(eq(offer), eq(buyer), eq("ACCEPTED"), eq(0L), any()))
                .thenReturn(Mono.just(accepted));
        assertThat(service.updateStatus(offer, buyer, new UpdateOfferStatusRequest("ACCEPTED", 0L))
                .block().acceptancePublicId()).isEqualTo(receipt);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.updateStatus(offer, seller,
                new UpdateOfferStatusRequest("ACCEPTED", 0L)).block()).hasMessageContaining("403");
        verify(repository, times(1)).updateStatus(any(), any(), any(), anyLong(), any());
    }

    private static ObjectProvider<OfferRepository> provider(OfferRepository repository) {
        return new ObjectProvider<>() {
            @Override public OfferRepository getObject(Object... args) { return repository; }
            @Override public OfferRepository getIfAvailable() { return repository; }
            @Override public OfferRepository getIfUnique() { return repository; }
        };
    }
}
