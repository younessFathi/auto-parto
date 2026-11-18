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
                .thenReturn(Mono.just("user-1"));
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
        when(deviceTokenService.getActiveTokens("user-1")).thenReturn(Mono.just(List.of("private-fcm-token")));
        when(firebaseMessaging.sendAsync(any())).thenReturn(ApiFutures.immediateFailedFuture(mockEx));

        StepVerifier.create(service.sendToUser("user-1", FcmNotification.sellerResponse("seller", "part", "response")))
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
        when(preferences.canSend("user-1", "push", "SELLER_RESPONSE"))
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

}
