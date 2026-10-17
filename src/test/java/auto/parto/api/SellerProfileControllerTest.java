package auto.parto.api;

import auto.parto.dto.SellerProfileResponse;
import auto.parto.security.CanonicalPrincipal;
import auto.parto.service.iservice.ISellerProfileService;
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

@WebFluxTest(controllers = SellerProfileController.class)
@Import({TestConfig.class, ReactiveMethodSecurityTestConfig.class})
@TestPropertySource(properties = {
        "auto.parto.persistence.postgresql.enabled=true",
        "auto.parto.persistence.postgresql.identity.enabled=true"
})
class SellerProfileControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private ISellerProfileService profileService;

    @Test
    void updatesUsingCanonicalPrincipalIdInsteadOfEmail() {
        UUID userPublicId = UUID.randomUUID();
        SellerProfileResponse response = new SellerProfileResponse(UUID.randomUUID(), userPublicId,
                "Grace", "Hopper", "+212600000000", "ENGINE", "Computing");
        when(profileService.update(eq(userPublicId), eq("Grace"), eq("Hopper"), eq("+212600000000"),
                eq("ENGINE"), eq("Computing"))).thenReturn(Mono.just(new auto.parto.persistence.SellerProfileRecord(
                response.publicId(), userPublicId, response.firstName(), response.lastName(),
                response.phoneNumber(), response.partType(), response.description())));

        webTestClient.mutateWith(org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers
                        .mockAuthentication(new TestAuthentication(
                                new CanonicalPrincipal(userPublicId.toString(), "different@example.test", "ROLE_SELLER"))))
                .put().uri("/seller/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"firstName":"Grace","lastName":"Hopper","phoneNumber":"+212600000000",
                         "partType":"ENGINE","description":"Computing"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.userPublicId").isEqualTo(userPublicId.toString());

        verify(profileService).update(userPublicId, "Grace", "Hopper", "+212600000000",
                "ENGINE", "Computing");
    }

    @Test
    void rejectsInvalidPhoneBeforeCallingService() {
        UUID userPublicId = UUID.randomUUID();

        webTestClient.mutateWith(org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers
                        .mockAuthentication(new TestAuthentication(
                                new CanonicalPrincipal(userPublicId.toString(), "seller@example.test", "ROLE_SELLER"))))
                .put().uri("/seller/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"firstName":"Grace","lastName":"Hopper","phoneNumber":"invalid",
                         "partType":"ENGINE","description":"Computing"}
                        """)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void rejectsNonCanonicalAuthentication() {
        webTestClient.put().uri("/seller/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"firstName":"Grace","lastName":"Hopper","phoneNumber":"+212600000000"}
                        """)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void rejectsWrongRoleBeforeCallingService() {
        webTestClient.mutateWith(org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers
                        .mockAuthentication(new TestAuthentication(
                                new CanonicalPrincipal(UUID.randomUUID().toString(), "buyer@example.test", "ROLE_BUYER"))))
                .put().uri("/seller/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"firstName\":\"Grace\",\"lastName\":\"Hopper\",\"phoneNumber\":\"+212600000000\",\"partType\":\"ENGINE\",\"description\":\"Computing\"}")
                .exchange()
                .expectStatus().isForbidden();

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
