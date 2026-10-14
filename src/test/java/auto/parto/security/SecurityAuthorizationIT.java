package auto.parto.security;

import auto.parto.config.BaseIntegrationTest;
import auto.parto.model.NotificationPreferences;
import auto.parto.repository.NotificationPreferencesRepository;
import auto.parto.service.SellerService;
import auto.parto.service.notification.BatchFcmResult;
import auto.parto.service.notification.DeviceTokenService;
import auto.parto.service.notification.FcmDeliveryResult;
import auto.parto.service.notification.ResilientFcmService;
import auto.parto.utils.SecurityHelper;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@AutoConfigureWebTestClient
class SecurityAuthorizationIT extends BaseIntegrationTest {

    private static final byte[] TEST_SECRET_BYTES = generatedKey();
    private static final String TEST_SECRET = Base64.getEncoder().encodeToString(TEST_SECRET_BYTES);
    private static final SecretKey TEST_SIGNING_KEY = Keys.hmacShaKeyFor(TEST_SECRET_BYTES);

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private NotificationPreferencesRepository preferencesRepository;

    @MockitoBean
    private ResilientFcmService fcmService;

    @MockitoBean
    private DeviceTokenService deviceTokenService;

    @MockitoBean
    private SellerService sellerService;

    @MockitoBean
    private SecurityHelper securityHelper;

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
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("AUTHENTICATION_REQUIRED")
                .jsonPath("$.message").isEqualTo("Authentication required")
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.requestId").isNotEmpty()
                .jsonPath("$.path").isEqualTo("/api/v1/notifications/preferences");
    }

    @Test
    void actuatorHealthAllowsUnauthenticatedAccess() {
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().value(status -> assertThat(status).isNotEqualTo(401));
    }

    @Test
    void protectedRouteRejectsMalformedToken() {
        requestPreferences("not-a-jwt")
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("AUTHENTICATION_FAILED")
                .jsonPath("$.message").isEqualTo("Authentication failed")
                .jsonPath("$.requestId").isNotEmpty();
    }

    @Test
    void protectedRouteRejectsExpiredToken() {
        String token = signedToken(TEST_SIGNING_KEY, builder -> builder.expiration(Date.from(Instant.now().minusSeconds(60))),
                Map.of("principal_id", "buyer-1", "email", "buyer@example.test", "roles", "ROLE_BUYER"));

        requestPreferences(token)
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("AUTHENTICATION_FAILED")
                .jsonPath("$.message").isEqualTo("Authentication failed");
    }

    @Test
    void protectedRouteRejectsIncorrectlySignedToken() {
        SecretKey wrongKey = Keys.hmacShaKeyFor(generatedKey());
        String token = signedToken(wrongKey, builder -> { },
                Map.of("principal_id", "buyer-1", "email", "buyer@example.test", "roles", "ROLE_BUYER"));

        requestPreferences(token)
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("AUTHENTICATION_FAILED")
                .jsonPath("$.message").isEqualTo("Authentication failed");
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
    void protectedRouteRejectsMissingCanonicalEmail() {
        requestPreferences(signedToken(TEST_SIGNING_KEY, builder -> { },
                Map.of("principal_id", "buyer-1", "roles", "ROLE_BUYER")))
                .expectStatus().isUnauthorized();
    }

    @Test
    void protectedRouteRejectsBlankCanonicalEmail() {
        requestPreferences(signedToken(TEST_SIGNING_KEY, builder -> { },
                Map.of("principal_id", "buyer-1", "email", " ", "roles", "ROLE_BUYER")))
                .expectStatus().isUnauthorized();
    }

    @Test
    void protectedRouteRejectsMissingCanonicalRoles() {
        requestPreferences(signedToken(TEST_SIGNING_KEY, builder -> { },
                Map.of("principal_id", "buyer-1", "email", "buyer@example.test")))
                .expectStatus().isUnauthorized();
    }

    @Test
    void protectedRouteRejectsBlankCanonicalRoles() {
        requestPreferences(signedToken(TEST_SIGNING_KEY, builder -> { },
                Map.of("principal_id", "buyer-1", "email", "buyer@example.test", "roles", " ")))
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
        when(securityHelper.validateAuthentication(any())).thenReturn(Mono.just(true));
        when(fcmService.sendToUser(anyString(), any())).thenReturn(Mono.just(
                FcmDeliveryResult.success("victim", "message-1")));
        webTestClient.post()
                .uri("/api/v1/notifications/fcm/send")
                .header("Authorization", "Bearer " + canonicalToken("admin-1", "admin@example.test", "ADMIN"))
                .bodyValue(Map.of("userId", "victim", "title", "test", "body", "test"))
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void fcmSearchNotificationRequiresBuyerRole() {
        assertForbiddenForFcmSearch("SELLER");
        assertForbiddenForFcmSearch("ADMIN");
        when(securityHelper.validateBuyer(any())).thenReturn(Mono.just(true));
        when(sellerService.findMatchingSellers()).thenReturn(Mono.just(java.util.List.of("seller-1")));
        when(fcmService.sendToUsers(any(), any())).thenReturn(Mono.just(
                BatchFcmResult.builder().totalUsers(1).successfulUsers(1).failedUsers(0).build()));
        webTestClient.post()
                .uri("/api/v1/notifications/fcm/notify/search")
                .header("Authorization", "Bearer " + canonicalToken("buyer-1", "buyer@example.test", "BUYER"))
                .bodyValue(Map.of("buyerId", "buyer-1", "partName", "brakes", "carMake", "Toyota",
                        "buyerLocation", "Casablanca"))
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void fcmResponseNotificationRequiresAdminDispatchRole() {
        assertForbiddenForFcmResponse("BUYER");
        assertForbiddenForFcmResponse("SELLER");
        when(securityHelper.validateAdmin(any())).thenReturn(Mono.just(true));
        when(fcmService.sendToUser(anyString(), any())).thenReturn(Mono.just(
                FcmDeliveryResult.success("buyer-1", "message-1")));
        assertResponseRouteSucceeds("ADMIN");
    }

    @Test
    void fcmUrgentNotificationRequiresAdminRole() {
        assertForbiddenForFcmUrgent("BUYER");
        assertForbiddenForFcmUrgent("SELLER");
        when(securityHelper.validateAdmin(any())).thenReturn(Mono.just(true));
        when(fcmService.sendToUsers(any(), any())).thenReturn(Mono.just(
                BatchFcmResult.builder().totalUsers(1).successfulUsers(1).failedUsers(0).build()));
        webTestClient.post()
                .uri("/api/v1/notifications/fcm/urgent")
                .header("Authorization", "Bearer " + canonicalToken("admin-1", "admin@example.test", "ADMIN"))
                .bodyValue(Map.of("userIds", java.util.List.of("victim"), "title", "test", "message", "test"))
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void fcmHealthRequiresAdminRole() {
        assertForbiddenForFcmHealth("BUYER");
        assertForbiddenForFcmHealth("SELLER");
        when(securityHelper.validateAdmin(any())).thenReturn(Mono.just(true));
        webTestClient.get()
                .uri("/api/v1/notifications/fcm/health")
                .header("Authorization", "Bearer " + canonicalToken("admin-1", "admin@example.test", "ADMIN"))
                .exchange()
                .expectStatus().isEqualTo(503);
    }

    @Test
    void fcmRegistrationRequiresCanonicalOwner() {
        when(deviceTokenService.registerToken(anyString(), any())).thenReturn(Mono.just(true));
        webTestClient.post()
                .uri("/api/v1/notifications/fcm/register")
                .header("Authorization", "Bearer " + canonicalToken("buyer-1", "buyer@example.test", "BUYER"))
                .bodyValue(Map.of("userId", "buyer-1", "deviceToken", "token-123456", "platform", "android"))
                .exchange()
                .expectStatus().isOk();

        webTestClient.post()
                .uri("/api/v1/notifications/fcm/register")
                .header("Authorization", "Bearer " + canonicalToken("buyer-1", "buyer@example.test", "BUYER"))
                .bodyValue(Map.of("userId", "other-buyer", "deviceToken", "token-123456", "platform", "android"))
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void fcmStatisticsAllowsOwnerAndAdminButRejectsOtherOwners() {
        when(deviceTokenService.getActiveTokens(anyString())).thenReturn(Mono.just(java.util.List.of("token-123456789")));

        requestFcmStats("buyer-1", "buyer-1", "BUYER")
                .expectStatus().isOk();
        requestFcmStats("buyer-1", "admin-1", "ADMIN")
                .expectStatus().isOk();
        requestFcmStats("buyer-1", "other-buyer", "BUYER")
                .expectStatus().isForbidden();
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

    private WebTestClient.ResponseSpec requestFcmStats(String userId, String principalId, String role) {
        return webTestClient.get()
                .uri("/api/v1/notifications/fcm/stats/{userId}", userId)
                .header("Authorization", "Bearer " + canonicalToken(principalId, principalId + "@example.test", role))
                .exchange();
    }

    private void assertResponseRouteSucceeds(String role) {
        webTestClient.post()
                .uri("/api/v1/notifications/fcm/notify/response")
                .header("Authorization", "Bearer " + canonicalToken("admin-1", "admin@example.test", role))
                .bodyValue(Map.of("buyerId", "buyer-1", "sellerId", "seller-1", "responseId", "response-1", "searchId", "search-1",
                        "sellerName", "Seller", "partName", "Brake pads"))
                .exchange()
                .expectStatus().isOk();
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
