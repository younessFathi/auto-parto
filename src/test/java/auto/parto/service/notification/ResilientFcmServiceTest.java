package auto.parto.service.notification;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import auto.parto.config.ProviderCallTelemetry;
import com.google.api.core.ApiFutures;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;
import reactor.core.publisher.Mono;

import java.util.List;
import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResilientFcmServiceTest {

    @Mock
    private FirebaseMessaging firebaseMessaging;
    @Mock
    private DeviceTokenService deviceTokenService;
    @Mock private NotificationPreferenceFilter preferences;
    private ResilientFcmService service;
    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(deviceTokenService.activeOwner(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Mono.just("00000000-0000-0000-0000-000000000001"));
        org.mockito.Mockito.lenient().when(preferences.canSend(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("push"), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Mono.just(true));
        circuitBreaker = CircuitBreaker.ofDefaults("fcm-test");
        service = new ResilientFcmService(firebaseMessaging, deviceTokenService, preferences, circuitBreaker,
                new ProviderCallTelemetry(new SimpleMeterRegistry()), Duration.ofSeconds(2),
                Duration.ofMillis(1), 0, 4, 4);
    }

    @Test
    void sendToTokenHandlesUnregisteredError() {
        FirebaseMessagingException mockEx = mock(FirebaseMessagingException.class);
        when(mockEx.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNREGISTERED);
        when(firebaseMessaging.sendAsync(any())).thenReturn(ApiFutures.immediateFailedFuture(mockEx));

        StepVerifier.create(service.sendToToken("token-1", FcmNotification.sellerResponse("seller", "part", "response")))
                .assertNext(result -> {
                    assert !result.isSuccess();
                    assert result.getToken() == null;
                })
                .verifyComplete();
    }

    @Test
    void sendToTokenHandlesInvalidArgumentError() {
        FirebaseMessagingException mockEx = mock(FirebaseMessagingException.class);
        when(mockEx.getMessagingErrorCode()).thenReturn(MessagingErrorCode.INVALID_ARGUMENT);
        when(firebaseMessaging.sendAsync(any())).thenReturn(ApiFutures.immediateFailedFuture(mockEx));

        StepVerifier.create(service.sendToToken("token-1", FcmNotification.sellerResponse("seller", "part", "response")))
                .assertNext(result -> {
                    assert !result.isSuccess();
                    assert result.getToken() == null;
                })
                .verifyComplete();
    }

    @Test
    void sendToUserDoesNotReturnFailedDeviceTokens() {
        FirebaseMessagingException mockEx = mock(FirebaseMessagingException.class);
        when(mockEx.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNREGISTERED);
        when(deviceTokenService.getActiveTokens("00000000-0000-0000-0000-000000000001")).thenReturn(Mono.just(List.of("private-fcm-token")));
        when(firebaseMessaging.sendAsync(any())).thenReturn(ApiFutures.immediateFailedFuture(mockEx));

        StepVerifier.create(service.sendToUser("00000000-0000-0000-0000-000000000001", FcmNotification.sellerResponse("seller", "part", "response")))
                .assertNext(result -> {
                    assert result.getFailedTokens().isEmpty();
                    assert result.getToken() == null;
                })
                .verifyComplete();
    }
    @Test
    void retryRechecksPreferencesAndOptOutDoesNotCallProviderAgain() {
        service = new ResilientFcmService(firebaseMessaging, deviceTokenService, preferences, circuitBreaker,
                new ProviderCallTelemetry(new SimpleMeterRegistry()), Duration.ofSeconds(2),
                Duration.ofMillis(1), 1, 4, 4);
        FirebaseMessagingException failure = mock(FirebaseMessagingException.class);
        when(failure.getMessagingErrorCode()).thenReturn(MessagingErrorCode.INTERNAL);
        when(firebaseMessaging.sendAsync(any())).thenReturn(ApiFutures.immediateFailedFuture(failure));
        when(preferences.canSend("00000000-0000-0000-0000-000000000001", "push", "SELLER_RESPONSE"))
                .thenReturn(Mono.just(true), Mono.just(false));
        StepVerifier.create(service.sendToToken("token-1", FcmNotification.sellerResponse("seller", "part", "response")))
                .expectNextMatches(result -> !result.isSuccess() && "SUPPRESSED".equals(result.getStatus()))
                .verifyComplete();
        org.mockito.Mockito.verify(firebaseMessaging, org.mockito.Mockito.times(1)).sendAsync(any());
    }

    @Test
    void unknownTypeAndUnregisteredTokenCannotBypassPreferences() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.sendToToken("token-1", new FcmNotification()))
                .isInstanceOf(IllegalArgumentException.class);
        when(deviceTokenService.activeOwner("token-1")).thenReturn(Mono.empty());
        StepVerifier.create(service.sendToToken("token-1", FcmNotification.sellerResponse("seller", "part", "response")))
                .expectNextMatches(result -> !result.isSuccess()).verifyComplete();
        org.mockito.Mockito.verifyNoInteractions(firebaseMessaging);
    }

    @Test
    void localIneligibleRecipientsCannotOpenSharedFirebaseBreaker() {
        circuitBreaker = CircuitBreaker.of("eligibility-isolation", io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
                .slidingWindowSize(5).minimumNumberOfCalls(5).failureRateThreshold(50).build());
        service = new ResilientFcmService(firebaseMessaging, deviceTokenService, preferences, circuitBreaker,
                new ProviderCallTelemetry(new SimpleMeterRegistry()), Duration.ofSeconds(2), Duration.ofMillis(1), 0, 4, 4);
        when(deviceTokenService.activeOwner("inactive")).thenReturn(Mono.empty());
        for (int i = 0; i < 5; i++) {
            StepVerifier.create(service.sendToToken("inactive", FcmNotification.sellerResponse("seller", "part", "response")))
                    .expectNextMatches(result -> !result.isSuccess()).verifyComplete();
        }
        org.assertj.core.api.Assertions.assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        org.assertj.core.api.Assertions.assertThat(circuitBreaker.getMetrics().getNumberOfBufferedCalls()).isZero();
        when(firebaseMessaging.sendAsync(any())).thenReturn(ApiFutures.immediateFuture("safe-message-id"));
        StepVerifier.create(service.sendToToken("eligible", FcmNotification.sellerResponse("seller", "part", "response")))
                .expectNextMatches(FcmDeliveryResult::isSuccess).verifyComplete();
        org.mockito.Mockito.verify(firebaseMessaging, org.mockito.Mockito.times(1)).sendAsync(any());
    }

    @Test
    void openProviderBreakerStillEvaluatesOptOutWithoutProviderAccounting() {
        circuitBreaker.transitionToOpenState();
        when(preferences.canSend("00000000-0000-0000-0000-000000000001", "push", "SELLER_RESPONSE")).thenReturn(Mono.just(false));
        StepVerifier.create(service.sendToToken("token-1", FcmNotification.sellerResponse("seller", "part", "response")))
                .expectNextMatches(result -> "SUPPRESSED".equals(result.getStatus())).verifyComplete();
        org.mockito.Mockito.verify(preferences).canSend("00000000-0000-0000-0000-000000000001", "push", "SELLER_RESPONSE");
        org.mockito.Mockito.verifyNoInteractions(firebaseMessaging);
        org.assertj.core.api.Assertions.assertThat(circuitBreaker.getMetrics().getNumberOfNotPermittedCalls()).isZero();
    }

    @Test
    void transientPreferenceFailureDoesNotCountAsFirebaseFailure() {
        when(preferences.canSend("00000000-0000-0000-0000-000000000001", "push", "SELLER_RESPONSE"))
                .thenReturn(Mono.error(new IllegalStateException("local lookup unavailable")));
        StepVerifier.create(service.sendToToken("token-1", FcmNotification.sellerResponse("seller", "part", "response")))
                .expectNextMatches(result -> !result.isSuccess() && !"SUPPRESSED".equals(result.getStatus())).verifyComplete();
        org.assertj.core.api.Assertions.assertThat(circuitBreaker.getMetrics().getNumberOfBufferedCalls()).isZero();
        org.mockito.Mockito.verifyNoInteractions(firebaseMessaging);
    }

    @Test
    void revokedOwnerIsTerminalAndProviderRetryRechecksEligibility() {
        service = new ResilientFcmService(firebaseMessaging, deviceTokenService, preferences, circuitBreaker,
                new ProviderCallTelemetry(new SimpleMeterRegistry()), Duration.ofSeconds(2), Duration.ofMillis(1), 2, 4, 4);
        when(deviceTokenService.activeOwner("revoked")).thenReturn(Mono.empty());
        StepVerifier.create(service.sendToToken("revoked", FcmNotification.sellerResponse("seller", "part", "response")))
                .expectNextMatches(result -> !result.isSuccess() && "INELIGIBLE".equals(result.getStatus())).verifyComplete();
        org.mockito.Mockito.verify(deviceTokenService, org.mockito.Mockito.times(1)).activeOwner("revoked");
        FirebaseMessagingException failure = mock(FirebaseMessagingException.class);
        when(failure.getMessagingErrorCode()).thenReturn(MessagingErrorCode.INTERNAL);
        when(firebaseMessaging.sendAsync(any())).thenReturn(ApiFutures.immediateFailedFuture(failure));
        when(deviceTokenService.activeOwner("retrying")).thenReturn(Mono.just("00000000-0000-0000-0000-000000000001"), Mono.empty());
        StepVerifier.create(service.sendToToken("retrying", FcmNotification.sellerResponse("seller", "part", "response")))
                .expectNextMatches(result -> !result.isSuccess()).verifyComplete();
        org.mockito.Mockito.verify(firebaseMessaging, org.mockito.Mockito.times(1)).sendAsync(any());
        org.mockito.Mockito.verify(deviceTokenService, org.mockito.Mockito.times(2)).activeOwner("retrying");
    }

    @Test
    void uppercaseRecipientMatchesCanonicalTokenOwner() {
        String canonical = "abcdefab-abcd-abcd-abcd-abcdefabcdef";
        String uppercase = canonical.toUpperCase(java.util.Locale.ROOT);
        when(deviceTokenService.getActiveTokens(org.mockito.ArgumentMatchers.anyString())).thenReturn(Mono.just(List.of("owned")));
        when(deviceTokenService.activeOwner("owned")).thenReturn(Mono.just(canonical));
        when(firebaseMessaging.sendAsync(any())).thenReturn(ApiFutures.immediateFuture("message-id"));
        StepVerifier.create(service.sendToUser(uppercase, FcmNotification.sellerResponse("seller", "part", "response")))
                .assertNext(result -> {
                    org.assertj.core.api.Assertions.assertThat(result.isSuccess()).isTrue();
                    org.assertj.core.api.Assertions.assertThat(result.getUserId()).isEqualTo(canonical);
                }).verifyComplete();
        org.mockito.Mockito.verify(deviceTokenService).getActiveTokens(canonical);
    }

    @Test
    void batchSuppressionIsSeparateFromDeliveryAndFailure() {
        when(deviceTokenService.getActiveTokens("00000000-0000-0000-0000-000000000011")).thenReturn(Mono.just(List.of("ok")));
        when(deviceTokenService.getActiveTokens("00000000-0000-0000-0000-000000000012")).thenReturn(Mono.just(List.of("off")));
        when(deviceTokenService.getActiveTokens("00000000-0000-0000-0000-000000000013")).thenReturn(Mono.just(List.of("bad")));
        when(deviceTokenService.activeOwner("ok")).thenReturn(Mono.just("00000000-0000-0000-0000-000000000011"));
        when(deviceTokenService.activeOwner("off")).thenReturn(Mono.just("00000000-0000-0000-0000-000000000012"));
        when(deviceTokenService.activeOwner("bad")).thenReturn(Mono.just("00000000-0000-0000-0000-000000000013"));
        when(preferences.canSend("00000000-0000-0000-0000-000000000012", "push", "SELLER_RESPONSE")).thenReturn(Mono.just(false));
        when(firebaseMessaging.sendAsync(any())).thenReturn(ApiFutures.immediateFuture("safe-id"));
        // A lookup failure is an actual failed recipient, not a suppression.
        when(preferences.canSend("00000000-0000-0000-0000-000000000013", "push", "SELLER_RESPONSE")).thenReturn(Mono.error(new IllegalStateException("lookup")));
        StepVerifier.create(service.sendToUsers(List.of("00000000-0000-0000-0000-000000000011", "00000000-0000-0000-0000-000000000012", "00000000-0000-0000-0000-000000000013"), FcmNotification.sellerResponse("seller", "part", "response")))
                .assertNext(result -> {
                    org.assertj.core.api.Assertions.assertThat(result.getSuccessfulUsers()).isEqualTo(1);
                    org.assertj.core.api.Assertions.assertThat(result.getFailedUsers()).isEqualTo(1);
                    org.assertj.core.api.Assertions.assertThat(result.getSuppressedUsers()).isEqualTo(1);
                }).verifyComplete();
        org.mockito.Mockito.verify(firebaseMessaging, org.mockito.Mockito.times(1)).sendAsync(any());
    }

    @Test
    void mixedDeviceSuppressionIsNotCountedAsProviderFailure() {
        String owner = "00000000-0000-0000-0000-000000000001";
        when(deviceTokenService.getActiveTokens(owner)).thenReturn(Mono.just(List.of("first-device", "second-device")));
        when(preferences.canSend(owner, "push", "SELLER_RESPONSE")).thenReturn(Mono.just(true), Mono.just(false));
        when(firebaseMessaging.sendAsync(any())).thenReturn(ApiFutures.immediateFuture("message-id"));
        StepVerifier.create(service.sendToUser(owner, FcmNotification.sellerResponse("seller", "part", "response")))
                .assertNext(result -> {
                    org.assertj.core.api.Assertions.assertThat(result.getStatus()).isEqualTo("PARTIAL");
                    org.assertj.core.api.Assertions.assertThat(result.getSuccessfulTokens()).isEqualTo(1);
                    org.assertj.core.api.Assertions.assertThat(result.getSuppressedTokens()).isEqualTo(1);
                    org.assertj.core.api.Assertions.assertThat(result.getFailedTokenCount()).isZero();
                }).verifyComplete();
        org.mockito.Mockito.verify(firebaseMessaging, org.mockito.Mockito.times(1)).sendAsync(any());
    }

    @Test
    void emptyRecipientBatchStillValidatesSharedCategory() {
        StepVerifier.create(service.sendToUsers(List.of(), new FcmNotification()))
                .expectError(IllegalArgumentException.class).verify();
        org.mockito.Mockito.verifyNoInteractions(firebaseMessaging);
    }

    @Test
    void actualProviderFailuresOpenBreakerAndAllowedOpenBreakerSendConsumesNoProviderCall() {
        circuitBreaker = CircuitBreaker.of("provider-failures", io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
                .slidingWindowSize(5).minimumNumberOfCalls(5).failureRateThreshold(50).build());
        service = new ResilientFcmService(firebaseMessaging, deviceTokenService, preferences, circuitBreaker,
                new ProviderCallTelemetry(new SimpleMeterRegistry()), Duration.ofSeconds(2), Duration.ofMillis(1), 0, 4, 4);
        FirebaseMessagingException failure = mock(FirebaseMessagingException.class);
        when(failure.getMessagingErrorCode()).thenReturn(MessagingErrorCode.INTERNAL);
        when(firebaseMessaging.sendAsync(any())).thenReturn(ApiFutures.immediateFailedFuture(failure));
        for (int i = 0; i < 5; i++) {
            StepVerifier.create(service.sendToToken("token-1", FcmNotification.sellerResponse("seller", "part", "response")))
                    .expectNextMatches(result -> !result.isSuccess()).verifyComplete();
        }
        org.assertj.core.api.Assertions.assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        StepVerifier.create(service.sendToToken("token-1", FcmNotification.sellerResponse("seller", "part", "response")))
                .expectNextMatches(result -> "FAILED".equals(result.getStatus())).verifyComplete();
        org.mockito.Mockito.verify(firebaseMessaging, org.mockito.Mockito.times(5)).sendAsync(any());
        org.assertj.core.api.Assertions.assertThat(circuitBreaker.getMetrics().getNumberOfNotPermittedCalls()).isEqualTo(1);
    }

}
