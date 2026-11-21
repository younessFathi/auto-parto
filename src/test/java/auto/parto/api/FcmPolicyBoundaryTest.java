package auto.parto.api;

import auto.parto.advicer.OtpExceptionHandler;
import auto.parto.config.ProviderCallTelemetry;
import auto.parto.service.SellerService;
import auto.parto.service.notification.*;
import auto.parto.utils.SecurityHelper;
import com.google.firebase.messaging.FirebaseMessaging;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockAuthentication;

/** HTTP boundary with real category, eligibility, preference and result aggregation code. */
@WebFluxTest(controllers = FcmNotificationController.class)
@Import({TestConfig.class, OtpExceptionHandler.class, FcmPolicyBoundaryTest.ServiceConfiguration.class})
class FcmPolicyBoundaryTest {
    private static final String USER = "abcdefab-abcd-abcd-abcd-abcdefabcdef";
    @Autowired WebTestClient client;
    @MockitoBean DeviceTokenService tokens;
    @MockitoBean FirebaseMessaging firebase;
    @MockitoBean NotificationPreferenceFilter preferences;
    @MockitoBean SellerService sellers;
    @MockitoBean SecurityHelper security;

    @TestConfiguration
    static class ServiceConfiguration {
        @Bean ResilientFcmService realFcmService(FirebaseMessaging firebase, DeviceTokenService tokens,
                                                NotificationPreferenceFilter preferences) {
            return new ResilientFcmService(firebase, tokens, preferences, CircuitBreaker.ofDefaults("http-policy"),
                    new ProviderCallTelemetry(new SimpleMeterRegistry()), Duration.ofSeconds(1),
                    Duration.ofMillis(1), 0, 4, 4);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", ",\"metadata\":null", ",\"metadata\":{}", ",\"metadata\":{\"type\":\"UNKNOWN\"}"})
    void urgentRejectsInvalidSharedCategoryBeforeAnyRecipientWork(String metadata) {
        when(security.validateAdmin(any())).thenReturn(Mono.just(true));
        client.mutateWith(mockAuthentication(CanonicalTestAuthentication.admin("admin"))).post()
                .uri("/api/v1/notifications/fcm/urgent").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"userIds\":[\"" + USER + "\"],\"title\":\"safe\",\"message\":\"safe\"" + metadata + "}")
                .exchange().expectStatus().isBadRequest();
        verifyNoInteractions(tokens, preferences, firebase);
    }

    @Test
    void allSuppressedUrgentBatchHasNoFailedDeliveriesOrProviderCalls() {
        when(security.validateAdmin(any())).thenReturn(Mono.just(true));
        suppressedRecipient();
        client.mutateWith(mockAuthentication(CanonicalTestAuthentication.admin("admin"))).post()
                .uri("/api/v1/notifications/fcm/urgent").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("userIds", List.of(USER), "title", "safe", "message", "safe",
                        "metadata", Map.of("type", "SELLER_RESPONSE")))
                .exchange().expectStatus().isOk().expectBody()
                .jsonPath("$.successfulUsers").isEqualTo(0)
                .jsonPath("$.failedUsers").isEqualTo(0)
                .jsonPath("$.suppressedUsers").isEqualTo(1)
                .jsonPath("$.results[0].fcmDeliveryResult.status").isEqualTo("SUPPRESSED");
        verifyNoInteractions(firebase);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/send", "/notify/response"})
    void singleRecipientSuppressionIsTerminalWithoutClaimingDelivery(String route) {
        lenient().when(security.validateAdmin(any())).thenReturn(Mono.just(true));
        lenient().when(security.validateAuthentication(any())).thenReturn(Mono.just(true));
        suppressedRecipient();
        Object body = route.equals("/send")
                ? Map.of("userId", USER, "title", "safe", "body", "safe", "data", Map.of("type", "SELLER_RESPONSE"))
                : Map.of("buyerId", USER, "sellerName", "safe", "partName", "safe", "responseId", "safe");
        client.mutateWith(mockAuthentication(CanonicalTestAuthentication.admin("admin"))).post()
                .uri("/api/v1/notifications/fcm" + route).contentType(MediaType.APPLICATION_JSON).bodyValue(body)
                .exchange().expectStatus().isOk().expectBody()
                .jsonPath("$.status").isEqualTo("SUPPRESSED")
                .jsonPath("$.success").isEqualTo(false);
        verifyNoInteractions(firebase);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/send", "/notify/response"})
    void ineligibleRecipientIsTerminalWithoutClaimingProviderFailure(String route) {
        lenient().when(security.validateAdmin(any())).thenReturn(Mono.just(true));
        lenient().when(security.validateAuthentication(any())).thenReturn(Mono.just(true));
        when(tokens.getActiveTokens(USER)).thenReturn(Mono.just(List.of("inactive-owner-token")));
        when(tokens.activeOwner("inactive-owner-token")).thenReturn(Mono.empty());
        Object body = route.equals("/send")
                ? Map.of("userId", USER, "title", "safe", "body", "safe", "data", Map.of("type", "SELLER_RESPONSE"))
                : Map.of("buyerId", USER, "sellerName", "safe", "partName", "safe", "responseId", "safe");
        client.mutateWith(mockAuthentication(CanonicalTestAuthentication.admin("admin"))).post()
                .uri("/api/v1/notifications/fcm" + route).contentType(MediaType.APPLICATION_JSON).bodyValue(body)
                .exchange().expectStatus().isOk().expectBody()
                .jsonPath("$.status").isEqualTo("INELIGIBLE")
                .jsonPath("$.success").isEqualTo(false).jsonPath("$.error").isEqualTo("NO_ACTIVE_OWNER");
        verifyNoInteractions(preferences, firebase);
    }

    @Test
    void emptyUrgentRecipientListStillRejectsUnknownSharedCategory() {
        when(security.validateAdmin(any())).thenReturn(Mono.just(true));
        client.mutateWith(mockAuthentication(CanonicalTestAuthentication.admin("admin"))).post()
                .uri("/api/v1/notifications/fcm/urgent").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("userIds", List.of(), "title", "safe", "message", "safe", "metadata", Map.of("type", "UNKNOWN")))
                .exchange().expectStatus().isBadRequest();
        verifyNoInteractions(tokens, preferences, firebase);
    }

    private void suppressedRecipient() {
        when(tokens.getActiveTokens(USER)).thenReturn(Mono.just(List.of("opaque-test-token")));
        when(tokens.activeOwner("opaque-test-token")).thenReturn(Mono.just(USER));
        when(preferences.canSend(USER, "push", "SELLER_RESPONSE")).thenReturn(Mono.just(false));
    }
}
