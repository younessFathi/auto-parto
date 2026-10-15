package auto.parto.api;

import auto.parto.dto.PartRequestResponse;
import auto.parto.service.PartRequestService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.context.TestPropertySource;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = PartRequestController.class)
@Import(TestConfig.class)
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

    private static PartRequestResponse response(UUID buyerId, String status) {
        Instant now = Instant.now();
        return new PartRequestResponse(UUID.randomUUID(), buyerId, "Brake pads", "Front axle", "NEW",
                "BRAKES", "Toyota", "Corolla", 2020, status, now, now, 0);
    }
}
