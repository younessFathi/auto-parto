package auto.parto.api;

import auto.parto.config.BaseIntegrationTest;
import auto.parto.service.iservice.IPieceService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import javax.crypto.SecretKey;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.function.Consumer;

@AutoConfigureWebTestClient
class ApiErrorContractContainerIT extends BaseIntegrationTest {

    private static final byte[] TEST_SECRET_BYTES = generatedKey();
    private static final String TEST_SECRET = Base64.getEncoder().encodeToString(TEST_SECRET_BYTES);
    private static final SecretKey TEST_SIGNING_KEY = Keys.hmacShaKeyFor(TEST_SECRET_BYTES);

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private IPieceService pieceService;

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("JWT_SECRET", () -> TEST_SECRET);
        registry.add("jwt.secretKey", () -> TEST_SECRET);
    }

    @Test
    void returnsSafeErrorContractThroughPostgresqlRedisBackedApplication() {
        webTestClient.get()
                .uri("/pieces/customer-1?page=-1&size=10")
                .header("Authorization", "Bearer " + canonicalToken())
                .header("X-Request-Id", "container-error-001")
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().valueEquals("X-Request-Id", "container-error-001")
                .expectBody()
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.code").isEqualTo("INVALID_REQUEST")
                .jsonPath("$.message").isEqualTo("Invalid request")
                .jsonPath("$.requestId").isEqualTo("container-error-001")
                .jsonPath("$.path").isEqualTo("/pieces/customer-1");
    }

    private String canonicalToken() {
        return signedToken(TEST_SIGNING_KEY, builder -> { },
                Map.of("principal_id", "customer-1", "email", "customer@example.test", "roles", "ROLE_BUYER"));
    }

    private String signedToken(SecretKey key, Consumer<JwtBuilder> customization, Map<String, String> claims) {
        Date now = new Date();
        JwtBuilder builder = Jwts.builder()
                .subject(claims.getOrDefault("email", "test@example.test"))
                .issuedAt(now)
                .expiration(Date.from(Instant.now().plusSeconds(300)));
        claims.forEach(builder::claim);
        customization.accept(builder);
        return builder.signWith(key).compact();
    }

    private static byte[] generatedKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return key;
    }
}
