package auto.parto.api;

import auto.parto.dto.WalletFundingResponse;
import auto.parto.service.WalletFundingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockAuthentication;

@WebFluxTest(WalletFundingController.class)
@Import(TestConfig.class)
class WalletFundingControllerTest {
    @Autowired private WebTestClient client;
    @MockitoBean private WalletFundingService service;
    private final UUID admin = UUID.randomUUID();
    private final UUID beneficiary = UUID.randomUUID();

    @Test
    void canonicalAdminCanRecordConfirmedManualFunding() {
        var result = new WalletFundingResponse(UUID.randomUUID(), "manual-001", beneficiary, 12500,
                "MAD", UUID.randomUUID(), UUID.randomUUID(), 12500, 1);
        when(service.confirm(eq(admin), any(), eq("funding-request"))).thenReturn(Mono.just(result));
        authorized(CanonicalTestAuthentication.admin(admin.toString()), body(true, 12500L))
                .expectStatus().isOk().expectBody()
                .jsonPath("$.fundingReference").isEqualTo("manual-001")
                .jsonPath("$.balanceAfterMinor").isEqualTo(12500);
    }

    @Test
    void sellersAndBuyersCannotAttestFunding() {
        authorized(CanonicalTestAuthentication.seller(beneficiary.toString()), body(true, 12500L))
                .expectStatus().isForbidden();
        authorized(CanonicalTestAuthentication.buyer(beneficiary.toString()), body(true, 12500L))
                .expectStatus().isForbidden();
        verifyNoInteractions(service);
    }

    @Test
    void anonymousRequestIsUnauthorized() {
        client.post().uri("/api/v1/admin/wallet-funding").header("Idempotency-Key", "funding-request")
                .bodyValue(body(true, 12500L)).exchange().expectStatus().isUnauthorized();
        verifyNoInteractions(service);
    }

    @Test
    void unconfirmedOrNonPositiveFundingIsBadRequest() {
        authorized(CanonicalTestAuthentication.admin(admin.toString()), body(false, 12500L))
                .expectStatus().isBadRequest();
        authorized(CanonicalTestAuthentication.admin(admin.toString()), body(true, 0L))
                .expectStatus().isBadRequest();
        authorized(CanonicalTestAuthentication.admin(admin.toString()), body(true, -1L))
                .expectStatus().isBadRequest();
        verifyNoInteractions(service);
    }

    @Test
    void requestKeyAndValidReferenceAreRequired() {
        client.mutateWith(mockAuthentication(CanonicalTestAuthentication.admin(admin.toString())))
                .post().uri("/api/v1/admin/wallet-funding").bodyValue(body(true, 100L))
                .exchange().expectStatus().isBadRequest();
        var invalid = new java.util.HashMap<>(body(true, 100L));
        invalid.put("fundingReference", " ");
        authorized(CanonicalTestAuthentication.admin(admin.toString()), invalid).expectStatus().isBadRequest();
        verifyNoInteractions(service);
    }

    private WebTestClient.ResponseSpec authorized(CanonicalTestAuthentication authentication, Map<String, Object> body) {
        return client.mutateWith(mockAuthentication(authentication)).post()
                .uri("/api/v1/admin/wallet-funding").header("Idempotency-Key", "funding-request")
                .bodyValue(body).exchange();
    }

    private Map<String, Object> body(boolean confirmed, long amount) {
        return Map.of("beneficiaryUserPublicId", beneficiary.toString(), "amountMinor", amount,
                "currency", "MAD", "fundingReference", "manual-001", "confirmed", confirmed);
    }
}
