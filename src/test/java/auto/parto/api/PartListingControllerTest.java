package auto.parto.api;

import auto.parto.dto.PartListingPageResponse;
import auto.parto.dto.PartListingResponse;
import auto.parto.service.PartListingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = PartListingController.class)
@Import({TestConfig.class})
@TestPropertySource(properties = "auto.parto.persistence.postgresql.enabled=true")
class PartListingControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private PartListingService service;

    @Test
    void createsListingUsingCanonicalSellerIdentity() {
        UUID sellerId = UUID.randomUUID();
        PartListingResponse response = response(sellerId, "DRAFT");
        when(service.create(eq(sellerId), any())).thenReturn(Mono.just(response));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(
                        CanonicalTestAuthentication.seller(sellerId.toString())))
                .post().uri("/api/v1/part-listings")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name":"Brake pad","description":"Front axle","condition":"NEW",
                         "category":"BRAKES","vehicleMake":"Toyota","vehicleModel":"Corolla",
                         "vehicleYear":2020,"amountMinor":12500,"currency":"MAD"}
                        """)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.sellerUserPublicId").isEqualTo(sellerId.toString())
                .jsonPath("$.businessStatus").isEqualTo("DRAFT");

        verify(service).create(eq(sellerId), any());
    }

    @Test
    void rejectsBuyerFromSellerListingEndpoint() {
        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(
                        CanonicalTestAuthentication.buyer(UUID.randomUUID().toString())))
                .get().uri("/api/v1/part-listings")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void appliesDefaultPagination() {
        UUID sellerId = UUID.randomUUID();
        when(service.findMine(sellerId, 0, 50)).thenReturn(Mono.just(new PartListingPageResponse(List.of(), 0, 0, 50)));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(
                        CanonicalTestAuthentication.seller(sellerId.toString())))
                .get().uri("/api/v1/part-listings")
                .exchange()
                .expectStatus().isOk();

        verify(service).findMine(sellerId, 0, 50);
    }

    @Test
    void rejectsInvalidPageSize() {
        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(
                        CanonicalTestAuthentication.seller(UUID.randomUUID().toString())))
                .get().uri("/api/v1/part-listings?page=-1&size=101")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void mapsInvalidLifecycleTransitionToConflict() {
        UUID sellerId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        when(service.transition(sellerId, listingId, "RESERVED")).thenReturn(Mono.error(
                new ResponseStatusException(HttpStatus.CONFLICT, "Part listing cannot transition from DRAFT")));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(
                        CanonicalTestAuthentication.seller(sellerId.toString())))
                .post().uri(uriBuilder -> uriBuilder.path("/api/v1/part-listings/{id}/transition")
                        .queryParam("status", "RESERVED").build(listingId))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void mapsDifferentSellerTransitionToForbidden() {
        UUID authenticatedSellerId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        when(service.transition(authenticatedSellerId, listingId, "CLOSED")).thenReturn(Mono.error(
                new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "The authenticated seller does not own this listing")));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(
                        CanonicalTestAuthentication.seller(authenticatedSellerId.toString())))
                .post().uri(uriBuilder -> uriBuilder.path("/api/v1/part-listings/{id}/transition")
                        .queryParam("status", "CLOSED").build(listingId))
                .exchange()
                .expectStatus().isForbidden();

        verify(service).transition(authenticatedSellerId, listingId, "CLOSED");
    }

    private static PartListingResponse response(UUID sellerId, String status) {
        Instant now = Instant.now();
        return new PartListingResponse(UUID.randomUUID(), sellerId, "Brake pad", "Front axle", "Toyota",
                "Corolla", 2020, "NEW", "BRAKES", 12500, "MAD", status, "NOT_READY", "NOT_STORED",
                0, now, now, 0);
    }
}
