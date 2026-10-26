package auto.parto.api;

import auto.parto.config.ApiErrorResponseWriter;
import auto.parto.config.SecurityConfig;
import auto.parto.service.RateLimitService;
import auto.parto.utils.JwtTokenGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;

@WebFluxTest(controllers = JwksController.class)
@Import(SecurityConfig.class)
class JwksControllerSecurityTest {
    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private JwtTokenGenerator jwtTokenGenerator;

    @MockitoBean
    private ApiErrorResponseWriter errorResponseWriter;

    @MockitoBean
    private RateLimitService rateLimitService;

    @Test
    void publishesPublicRsaKeysWithoutAuthenticationOrPrivateMaterial() {
        when(jwtTokenGenerator.publishedJwks()).thenReturn(Map.of("keys", List.of(Map.of(
                "kty", "RSA", "use", "sig", "alg", "RS256", "kid", "active",
                "n", "public-modulus", "e", "AQAB"))));

        webTestClient.get().uri("/.well-known/jwks.json")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.keys[0].kty").isEqualTo("RSA")
                .jsonPath("$.keys[0].alg").isEqualTo("RS256")
                .jsonPath("$.keys[0].kid").isEqualTo("active")
                .jsonPath("$.keys[0].n").isEqualTo("public-modulus")
                .jsonPath("$.keys[0].e").isEqualTo("AQAB")
                .jsonPath("$.keys[0].d").doesNotExist()
                .jsonPath("$.keys[0].privateKey").doesNotExist();
    }
}
