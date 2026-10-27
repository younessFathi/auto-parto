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
    private ResilientFcmService service;
    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        circuitBreaker = CircuitBreaker.ofDefaults("fcm-test");
        service = new ResilientFcmService(firebaseMessaging, deviceTokenService, circuitBreaker,
                new ProviderCallTelemetry(new SimpleMeterRegistry()), Duration.ofSeconds(2),
                Duration.ofMillis(1), 0, 4, 4);
    }

    @Test
    void sendToTokenHandlesUnregisteredError() {
        FirebaseMessagingException mockEx = mock(FirebaseMessagingException.class);
        when(mockEx.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNREGISTERED);
        when(firebaseMessaging.sendAsync(any())).thenReturn(ApiFutures.immediateFailedFuture(mockEx));

        StepVerifier.create(service.sendToToken("token-1", new FcmNotification()))
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

        StepVerifier.create(service.sendToToken("token-1", new FcmNotification()))
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

        StepVerifier.create(service.sendToUser("user-1", new FcmNotification()))
                .assertNext(result -> {
                    assert result.getFailedTokens().isEmpty();
                    assert result.getToken() == null;
                })
                .verifyComplete();
    }
}
