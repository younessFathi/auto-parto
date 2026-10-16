package auto.parto.api;

import auto.parto.dto.PartListingMatchResponse;
import auto.parto.dto.PartListingResponse;
import auto.parto.service.MarketplaceMatchingService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MarketplaceMatchingControllerTest {
    private final MarketplaceMatchingService service = mock(MarketplaceMatchingService.class);
    private final MarketplaceMatchingController controller = new MarketplaceMatchingController(service);
    private final UUID buyer = UUID.randomUUID();
    private final UUID request = UUID.randomUUID();

    @Test
    void derivesBuyerFromCanonicalPrincipalAndUsesDefaultLimit() {
        PartListingMatchResponse response = new PartListingMatchResponse(List.of(), 20);
        when(service.findMatches(buyer, request, 20)).thenReturn(Mono.just(response));

        assertThat(controller.findMatches(request, 20, auth()).block()).isSameAs(response);
        verify(service).findMatches(buyer, request, 20);
    }

    @Test
    void rejectsLimitAboveFanoutBound() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> controller.findMatches(request, 51, auth()).block())
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("between 1 and 50");
        verifyNoInteractions(service);
    }

    private Authentication auth() {
        CanonicalTestAuthentication authentication = CanonicalTestAuthentication.buyer(buyer.toString());
        return new UsernamePasswordAuthenticationToken(authentication.getPrincipal(), "token",
                authentication.getAuthorities());
    }
}
