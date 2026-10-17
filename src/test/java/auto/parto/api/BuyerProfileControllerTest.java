package auto.parto.api;

import auto.parto.persistence.BuyerProfileRecord;
import auto.parto.security.CanonicalPrincipal;
import auto.parto.service.iservice.IBuyerProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = BuyerProfileController.class)
@Import({TestConfig.class, ReactiveMethodSecurityTestConfig.class})
@TestPropertySource(properties = {
        "auto.parto.persistence.postgresql.enabled=true",
        "auto.parto.persistence.postgresql.buyer-identity.enabled=true"
})
class BuyerProfileControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private IBuyerProfileService profileService;

    @Test
    void updatesUsingCanonicalPrincipalIdInsteadOfEmail() {
        UUID userPublicId = UUID.randomUUID();
        UUID profilePublicId = UUID.randomUUID();
        when(profileService.update(eq(userPublicId), eq("Ada"), eq("Lovelace"), eq("+212600000000"),
                eq("https://example.test/avatar"))).thenReturn(Mono.just(new BuyerProfileRecord(
                profilePublicId, userPublicId, "Ada", "Lovelace", "+212600000000",
                "https://example.test/avatar")));

        webTestClient.mutateWith(org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers
                        .mockAuthentication(new TestAuthentication(
                                new CanonicalPrincipal(userPublicId.toString(), "different@example.test", "ROLE_BUYER"))))
                .put().uri("/customers/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"firstName":"Ada","lastName":"Lovelace","phoneNumber":"+212600000000",
                         "pictureUrl":"https://example.test/avatar"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.userPublicId").isEqualTo(userPublicId.toString());

        verify(profileService).update(userPublicId, "Ada", "Lovelace", "+212600000000",
                "https://example.test/avatar");
    }

    @Test
    void rejectsInvalidPhoneBeforeCallingService() {
        UUID userPublicId = UUID.randomUUID();

        webTestClient.mutateWith(org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers
                        .mockAuthentication(new TestAuthentication(
                                new CanonicalPrincipal(userPublicId.toString(), "buyer@example.test", "ROLE_BUYER"))))
                .put().uri("/customers/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"phoneNumber\":\"invalid\"}")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void rejectsWrongRoleBeforeCallingService() {
        webTestClient.mutateWith(org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers
                        .mockAuthentication(new TestAuthentication(
                                new CanonicalPrincipal(UUID.randomUUID().toString(), "seller@example.test", "ROLE_SELLER"))))
                .put().uri("/customers/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"firstName\":\"Ada\",\"lastName\":\"Lovelace\",\"phoneNumber\":\"+212600000000\"}")
                .exchange()
                .expectStatus().isForbidden();

        verifyNoInteractions(profileService);
    }

    @Test
    void rejectsMissingAuthenticationBeforeCallingService() {
        webTestClient.put().uri("/customers/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"firstName\":\"Ada\"}")
                .exchange()
                .expectStatus().isUnauthorized();

        verifyNoInteractions(profileService);
    }

    private record TestAuthentication(CanonicalPrincipal principal) implements Authentication {
        @Override public java.util.Collection<? extends org.springframework.security.core.GrantedAuthority> getAuthorities() {
            return principal.getAuthorities();
        }
        @Override public Object getCredentials() { return ""; }
        @Override public Object getDetails() { return null; }
        @Override public Object getPrincipal() { return principal; }
        @Override public boolean isAuthenticated() { return true; }
        @Override public void setAuthenticated(boolean authenticated) { }
        @Override public String getName() { return principal.getUsername(); }
    }
}
