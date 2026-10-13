package auto.parto.security;

import auto.parto.config.BaseIntegrationTest;
import auto.parto.model.NotificationPreferences;
import auto.parto.repository.NotificationPreferencesRepository;
import auto.parto.utils.JwtTokenGenerator;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import javax.crypto.SecretKey;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureWebTestClient
class SecurityAuthorizationIT extends BaseIntegrationTest {

    private static final byte[] TEST_SECRET_BYTES = generatedKey();
    private static final String TEST_SECRET = Base64.getEncoder().encodeToString(TEST_SECRET_BYTES);
    private static final SecretKey TEST_SIGNING_KEY = Keys.hmacShaKeyFor(TEST_SECRET_BYTES);

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private JwtTokenGenerator jwtTokenGenerator;

    @Autowired
    private NotificationPreferencesRepository preferencesRepository;

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("JWT_SECRET", () -> TEST_SECRET);
        registry.add("jwt.secretKey", () -> TEST_SECRET);
    }

    @BeforeEach
    void clearPreferences() {
        preferencesRepository.deleteAll().block();
    }

    @Test
    void protectedRouteRejectsMissingToken() {
        requestPreferences(null)
                .expectStatus().isUnauthorized();
    }

    @Test
    void protectedRouteRejectsMalformedToken() {
        requestPreferences("not-a-jwt")
                .expectStatus().isUnauthorized();
    }

    @Test
    void protectedRouteRejectsExpiredToken() {
        String token = signedToken(TEST_SIGNING_KEY, builder -> builder.expiration(Date.from(Instant.now().minusSeconds(60))),
                Map.of("principal_id", "buyer-1", "email", "buyer@example.test", "roles", "ROLE_BUYER"));

        requestPreferences(token)
                .expectStatus().isUnauthorized();
    }

    @Test
    void protectedRouteRejectsIncorrectlySignedToken() {
        SecretKey wrongKey = Keys.hmacShaKeyFor(generatedKey());
        String token = signedToken(wrongKey, builder -> { },
                Map.of("principal_id", "buyer-1", "email", "buyer@example.test", "roles", "ROLE_BUYER"));

        requestPreferences(token)
                .expectStatus().isUnauthorized();
    }

    @Test
    void protectedRouteRejectsMissingCanonicalIdentity() {
        String token = signedToken(TEST_SIGNING_KEY, builder -> { },
                Map.of("email", "buyer@example.test", "roles", "ROLE_BUYER"));

        requestPreferences(token)
                .expectStatus().isUnauthorized();
    }

    @Test
    void protectedRouteRejectsBlankCanonicalIdentity() {
        String token = signedToken(TEST_SIGNING_KEY, builder -> { },
                Map.of("principal_id", " ", "email", "buyer@example.test", "roles", "ROLE_BUYER"));

        requestPreferences(token)
                .expectStatus().isUnauthorized();
    }

    @Test
    void protectedRouteRejectsUnsupportedRole() {
        String token = signedToken(TEST_SIGNING_KEY, builder -> { },
                Map.of("principal_id", "buyer-1", "email", "buyer@example.test", "roles", "ROLE_OPERATOR"));

        requestPreferences(token)
                .expectStatus().isUnauthorized();
    }

    @Test
    void protectedRouteRejectsMultipleRoles() {
        String token = signedToken(TEST_SIGNING_KEY, builder -> { },
                Map.of("principal_id", "buyer-1", "email", "buyer@example.test", "roles", "ROLE_BUYER,ROLE_ADMIN"));

        requestPreferences(token)
                .expectStatus().isUnauthorized();
    }

    @Test
    void fcmSendRequiresAdminRole() {
        assertForbiddenForFcmSend("BUYER");
        assertForbiddenForFcmSend("SELLER");
        assertFcmRouteIsNotForbidden("ADMIN", "/api/v1/notifications/fcm/send", "POST");
    }

    @Test
    void fcmSearchNotificationRequiresBuyerRole() {
        assertForbiddenForFcmSearch("SELLER");
        assertForbiddenForFcmSearch("ADMIN");
    }

    @Test
    void fcmResponseNotificationRequiresAdminDispatchRole() {
        assertForbiddenForFcmResponse("BUYER");
        assertForbiddenForFcmResponse("SELLER");
        assertFcmRouteIsNotForbidden("ADMIN", "/api/v1/notifications/fcm/notify/response", "POST");
    }

    @Test
    void fcmUrgentNotificationRequiresAdminRole() {
        assertForbiddenForFcmUrgent("BUYER");
        assertForbiddenForFcmUrgent("SELLER");
        assertFcmRouteIsNotForbidden("ADMIN", "/api/v1/notifications/fcm/urgent", "POST");
    }

    @Test
    void fcmHealthRequiresAdminRole() {
        assertForbiddenForFcmHealth("BUYER");
        assertForbiddenForFcmHealth("SELLER");
        assertFcmRouteIsNotForbidden("ADMIN", "/api/v1/notifications/fcm/health", "GET");
    }

    @Test
    void preferenceRouteUsesCanonicalIdAndMigratesLegacyEmailRecord() {
        NotificationPreferences legacy = NotificationPreferences.builder()
                .userId("buyer@example.test")
                .emailEnabled(true)
                .pushEnabled(false)
                .smsEnabled(true)
                .newOfferEnabled(false)
                .priceAlertEnabled(true)
                .searchMatchEnabled(false)
                .orderUpdateEnabled(true)
                .marketingEnabled(false)
                .build();
        preferencesRepository.save(legacy).block();

        requestPreferences(canonicalToken("buyer-public-id", "buyer@example.test", "BUYER"))
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.userId").isEqualTo("buyer-public-id")
                .jsonPath("$.pushEnabled").isEqualTo(false)
                .jsonPath("$.searchMatchEnabled").isEqualTo(false);

        NotificationPreferences migrated = preferencesRepository.findByUserId("buyer-public-id").block();
        assertThat(migrated).isNotNull();
        assertThat(migrated.getUserId()).isEqualTo("buyer-public-id");
        assertThat(preferencesRepository.findByUserId("buyer@example.test").block()).isNull();
    }

    private WebTestClient.ResponseSpec requestPreferences(String token) {
        WebTestClient.RequestHeadersSpec<?> request = webTestClient.get()
                .uri("/api/v1/notifications/preferences");
        if (token == null) {
            return request.exchange();
        }
        return request.header("Authorization", "Bearer " + token).exchange();
    }

    private void assertForbiddenForFcmSend(String role) {
        webTestClient.post()
                .uri("/api/v1/notifications/fcm/send")
                .header("Authorization", "Bearer " + canonicalToken(role.toLowerCase() + "-1", role.toLowerCase() + "@example.test", role))
                .bodyValue(Map.of("userId", "victim", "title", "test", "body", "test"))
                .exchange()
                .expectStatus().isForbidden();
    }

    private void assertForbiddenForFcmSearch(String role) {
        webTestClient.post()
                .uri("/api/v1/notifications/fcm/notify/search")
                .header("Authorization", "Bearer " + canonicalToken("actor-1", "actor@example.test", role))
                .bodyValue(Map.of("buyerId", "buyer-1", "partName", "brakes", "carMake", "Toyota"))
                .exchange()
                .expectStatus().isForbidden();
    }

    private void assertForbiddenForFcmResponse(String role) {
        webTestClient.post()
                .uri("/api/v1/notifications/fcm/notify/response")
                .header("Authorization", "Bearer " + canonicalToken("actor-1", "actor@example.test", role))
                .bodyValue(Map.of("buyerId", "buyer-1", "sellerId", "seller-1", "responseId", "response-1", "searchId", "search-1"))
                .exchange()
                .expectStatus().isForbidden();
    }

    private void assertForbiddenForFcmUrgent(String role) {
        webTestClient.post()
                .uri("/api/v1/notifications/fcm/urgent")
                .header("Authorization", "Bearer " + canonicalToken("actor-1", "actor@example.test", role))
                .bodyValue(Map.of("userIds", Stream.of("victim").toList(), "title", "test", "message", "test"))
                .exchange()
                .expectStatus().isForbidden();
    }

    private void assertForbiddenForFcmHealth(String role) {
        webTestClient.get()
                .uri("/api/v1/notifications/fcm/health")
                .header("Authorization", "Bearer " + canonicalToken("actor-1", "actor@example.test", role))
                .exchange()
                .expectStatus().isForbidden();
    }

    private void assertFcmRouteIsNotForbidden(String role, String uri, String method) {
        WebTestClient.RequestHeadersSpec<?> request;
        if ("GET".equals(method)) {
            request = webTestClient.get().uri(uri);
        } else {
            request = webTestClient.post().uri(uri)
                    .bodyValue(notificationBody(uri));
        }
        request.header("Authorization", "Bearer " + canonicalToken("admin-1", "admin@example.test", role))
                .exchange()
                .expectStatus().value(status -> assertThat(status).isNotEqualTo(403));
    }

    private Map<String, Object> notificationBody(String uri) {
        if (uri.endsWith("/urgent")) {
            return Map.of("userIds", Stream.of("victim").toList(), "title", "test", "message", "test");
        }
        if (uri.endsWith("/response")) {
            return Map.of("buyerId", "buyer-1", "sellerId", "seller-1", "responseId", "response-1", "searchId", "search-1");
        }
        return Map.of("userId", "victim", "title", "test", "body", "test");
    }

    private String canonicalToken(String id, String email, String role) {
        return signedToken(TEST_SIGNING_KEY, builder -> { },
                Map.of("principal_id", id, "email", email, "roles", "ROLE_" + role));
    }

    private String signedToken(SecretKey key, Consumer<JwtBuilder> customization, Map<String, String> claims) {
        Date now = new Date();
        JwtBuilder builder = Jwts.builder()
                .subject(claims.getOrDefault("email", "test@example.test"))
                .issuedAt(now)
                .expiration(Date.from(Instant.now().plusSeconds(300)));
        claims.forEach((name, value) -> builder.claim(name, value));
        customization.accept(builder);
        return builder.signWith(key).compact();
    }

    private static byte[] generatedKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return key;
    }
}
