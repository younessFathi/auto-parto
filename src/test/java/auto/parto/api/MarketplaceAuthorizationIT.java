package auto.parto.api;

import auto.parto.advicer.OtpExceptionHandler;
import auto.parto.service.MarketplaceMatchingService;
import auto.parto.service.PostgresqlOfferService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = {MarketplaceMatchingController.class, OfferController.class})
@Import({TestConfig.class, OtpExceptionHandler.class})
@TestPropertySource(properties = "auto.parto.persistence.postgresql.enabled=true")
class MarketplaceAuthorizationIT {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private MarketplaceMatchingService matchingService;

    @MockitoBean
    private PostgresqlOfferService offerService;

    @Test
    void nonBuyerCannotCallMatchingEndpoint() {
        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(
                        CanonicalTestAuthentication.seller(UUID.randomUUID().toString())))
                .get().uri("/api/v1/part-requests/{id}/matches", UUID.randomUUID())
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void invalidMatchingLimitReturnsBadRequest() {
        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(
                        CanonicalTestAuthentication.buyer(UUID.randomUUID().toString())))
                .get().uri(uriBuilder -> uriBuilder.path("/api/v1/part-requests/{id}/matches")
                        .queryParam("limit", 51).build(UUID.randomUUID()))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void nonOwnerMatchingRequestReturnsForbidden() {
        UUID buyer = UUID.randomUUID();
        UUID request = UUID.randomUUID();
        when(matchingService.findMatches(eq(buyer), eq(request), eq(20)))
                .thenReturn(Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "The authenticated buyer does not own this request")));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(
                        CanonicalTestAuthentication.buyer(buyer.toString())))
                .get().uri("/api/v1/part-requests/{id}/matches", request)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void nonOpenMatchingRequestReturnsConflict() {
        UUID buyer = UUID.randomUUID();
        UUID request = UUID.randomUUID();
        when(matchingService.findMatches(eq(buyer), eq(request), eq(20)))
                .thenReturn(Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,
                        "Only open part requests can be matched")));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(
                        CanonicalTestAuthentication.buyer(buyer.toString())))
                .get().uri("/api/v1/part-requests/{id}/matches", request)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void unrelatedOfferMutationReturnsForbidden() {
        UUID actor = UUID.randomUUID();
        UUID offer = UUID.randomUUID();
        when(offerService.updateStatus(eq(offer), eq(actor), any()))
                .thenReturn(Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "The authenticated principal does not own this offer")));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(
                        CanonicalTestAuthentication.seller(actor.toString())))
                .patch().uri("/offers/{id}/status", offer)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"status\":\"ACCEPTED\",\"expectedVersion\":0}")
                .exchange()
                .expectStatus().isForbidden();
    }
}
