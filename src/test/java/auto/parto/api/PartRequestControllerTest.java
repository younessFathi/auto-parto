package auto.parto.api;

import auto.parto.advicer.OtpExceptionHandler;
import auto.parto.dto.PartRequestPageResponse;
import auto.parto.dto.PartRequestResponse;
import auto.parto.exception.EntityNotFoundException;
import auto.parto.service.PartRequestService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.context.TestPropertySource;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = PartRequestController.class)
@Import({TestConfig.class, OtpExceptionHandler.class})
@TestPropertySource(properties = "auto.parto.persistence.postgresql.enabled=true")
class PartRequestControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private PartRequestService service;

    @Test
    void createsRequestUsingCanonicalBuyerIdentity() {
        UUID buyerId = UUID.randomUUID();
        PartRequestResponse response = response(buyerId, "OPEN");
        when(service.create(eq(buyerId), any())).thenReturn(Mono.just(response));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(
                        CanonicalTestAuthentication.buyer(buyerId.toString())))
                .post().uri("/api/v1/part-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name":"Brake pads","description":"Front axle","condition":"NEW",
                         "category":"BRAKES","vehicleMake":"Toyota","vehicleModel":"Corolla","vehicleYear":2020}
                        """)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.publicId").isEqualTo(response.publicId().toString())
                .jsonPath("$.buyerUserPublicId").isEqualTo(buyerId.toString())
                .jsonPath("$.status").isEqualTo("OPEN");

        verify(service).create(eq(buyerId), any());
    }

    @Test
    void rejectsInvalidRequestBeforeCallingService() {
        UUID buyerId = UUID.randomUUID();

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(
                        CanonicalTestAuthentication.buyer(buyerId.toString())))
                .post().uri("/api/v1/part-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"\"}")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void rejectsSellerFromBuyerRequestEndpoint() {
        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(
                        CanonicalTestAuthentication.seller(UUID.randomUUID().toString())))
                .get().uri("/api/v1/part-requests")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void listsRequestsWithValidatedDefaultPagination() {
        UUID buyerId = UUID.randomUUID();
        when(service.findMine(buyerId, null, 50)).thenReturn(Mono.just(new PartRequestPageResponse(
                java.util.List.of(), null)));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(
                        CanonicalTestAuthentication.buyer(buyerId.toString())))
                .get().uri("/api/v1/part-requests")
                .exchange()
                .expectStatus().isOk();

        verify(service).findMine(buyerId, null, 50);
    }

    @Test
    void rejectsInvalidLimit() {
        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(
                        CanonicalTestAuthentication.buyer(UUID.randomUUID().toString())))
                .get().uri("/api/v1/part-requests?limit=101")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void rejectsMalformedCursor() {
        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(
                        CanonicalTestAuthentication.buyer(UUID.randomUUID().toString())))
                .get().uri("/api/v1/part-requests?cursor=not-a-cursor")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void returnsItemsAndNextCursor() {
        UUID buyerId = UUID.randomUUID();
        PartRequestResponse item = response(buyerId, "OPEN");
        String cursor = Base64.getUrlEncoder().withoutPadding().encodeToString(
                (item.createdAt() + "|" + item.publicId()).getBytes(StandardCharsets.UTF_8));
        when(service.findMine(buyerId, cursor, 2)).thenReturn(Mono.just(
                new PartRequestPageResponse(java.util.List.of(item), "next-cursor")));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(
                        CanonicalTestAuthentication.buyer(buyerId.toString())))
                .get().uri(uriBuilder -> uriBuilder.path("/api/v1/part-requests")
                        .queryParam("cursor", cursor).queryParam("limit", 2).build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.items[0].publicId").isEqualTo(item.publicId().toString())
                .jsonPath("$.nextCursor").isEqualTo("next-cursor");

        verify(service).findMine(buyerId, cursor, 2);
    }

    @Test
    void mapsDifferentBuyerCloseToForbidden() {
        UUID buyerId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        when(service.close(buyerId, requestId)).thenReturn(Mono.error(
                new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN,
                        "The authenticated buyer does not own this request")));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(
                        CanonicalTestAuthentication.buyer(buyerId.toString())))
                .post().uri("/api/v1/part-requests/{id}/close", requestId)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void mapsClosedRequestTransitionToConflict() {
        UUID buyerId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        when(service.cancel(buyerId, requestId)).thenReturn(Mono.error(
                new org.springframework.web.server.ResponseStatusException(HttpStatus.CONFLICT,
                        "Part request cannot transition from CLOSED")));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(
                        CanonicalTestAuthentication.buyer(buyerId.toString())))
                .post().uri("/api/v1/part-requests/{id}/cancel", requestId)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void mapsMissingRequestToNotFound() {
        UUID buyerId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        when(service.close(buyerId, requestId)).thenReturn(Mono.error(
                new EntityNotFoundException("Part request not found")));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(
                        CanonicalTestAuthentication.buyer(buyerId.toString())))
                .post().uri("/api/v1/part-requests/{id}/close", requestId)
                .exchange()
                .expectStatus().isNotFound();
    }

    private static PartRequestResponse response(UUID buyerId, String status) {
        Instant now = Instant.now();
        return new PartRequestResponse(UUID.randomUUID(), buyerId, "Brake pads", "Front axle", "NEW",
                "BRAKES", "Toyota", "Corolla", 2020, status, now, now, 0);
    }
}
