package auto.parto.api;

import auto.parto.dto.CreateOfferRequest;
import auto.parto.dto.OfferResponse;
import auto.parto.dto.UpdateOfferStatusRequest;
import auto.parto.security.CanonicalPrincipal;
import auto.parto.service.PostgresqlOfferService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class OfferControllerTest {
    private final PostgresqlOfferService service = mock(PostgresqlOfferService.class);
    private final OfferController controller = new OfferController(service);
    private final UUID actor = UUID.randomUUID();
    private final UUID target = UUID.randomUUID();
    private final CreateOfferRequest create = new CreateOfferRequest(1000L, "MAD", true, 0L, null);
    private final OfferResponse response = new OfferResponse(UUID.randomUUID(), target, null, actor,
            UUID.randomUUID(), 1000, "MAD", "PENDING", true, 0, null, null, null);

    @Test
    void requestOfferRequiresSellerAndUsesPrincipal() {
        when(service.createForRequest(actor, target, create)).thenReturn(Mono.just(response));

        assertThat(controller.createForRequest(target, create, auth("ROLE_SELLER")).block()).isSameAs(response);
        verify(service).createForRequest(actor, target, create);
    }

    @Test
    void listingOfferRequiresBuyer() {
        when(service.createForListing(actor, target, create)).thenReturn(Mono.just(response));

        assertThat(controller.createForListing(target, create, auth("ROLE_BUYER")).block()).isSameAs(response);
        verify(service).createForListing(actor, target, create);
    }

    @Test
    void statusUpdateUsesAuthenticatedActor() {
        UpdateOfferStatusRequest request = new UpdateOfferStatusRequest("ACCEPTED", 0L);
        UUID offer = response.publicId();
        when(service.updateStatus(offer, actor, request)).thenReturn(Mono.just(response));

        assertThat(controller.updateStatus(offer, request, auth("ROLE_SELLER")).block()).isSameAs(response);
        verify(service).updateStatus(offer, actor, request);
    }

    private Authentication auth(String role) {
        CanonicalPrincipal principal = new CanonicalPrincipal(actor.toString(), "user@example.test", role);
        return new UsernamePasswordAuthenticationToken(principal, "token", principal.getAuthorities());
    }
}
