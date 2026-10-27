package auto.parto.service.notification;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.test.StepVerifier;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResilientFcmServiceTest {

    @Mock
    private FirebaseMessaging firebaseMessaging;
    @Mock
    private DeviceTokenService deviceTokenService;
    @Mock
    private RetryTemplate retryTemplate;
    private ResilientFcmService service;
    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        circuitBreaker = CircuitBreaker.ofDefaults("fcm-test");
        service = new ResilientFcmService(firebaseMessaging, deviceTokenService, circuitBreaker, retryTemplate);
        ReflectionTestUtils.setField(service, "fcmTimeout", Duration.ofSeconds(10));
    }

    @Test
    void sendToTokenHandlesUnregisteredError() throws Exception {
        FirebaseMessagingException mockEx = mock(FirebaseMessagingException.class);
        when(mockEx.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNREGISTERED);
        when(firebaseMessaging.send(any())).thenThrow(mockEx);

        StepVerifier.create(service.sendToToken("token-1", new FcmNotification()))
                .assertNext(result -> {
                    assert !result.isSuccess();
                    assert result.getToken() == null;
                })
                .verifyComplete();
    }

    @Test
    void sendToTokenHandlesInvalidArgumentError() throws Exception {
        FirebaseMessagingException mockEx = mock(FirebaseMessagingException.class);
        when(mockEx.getMessagingErrorCode()).thenReturn(MessagingErrorCode.INVALID_ARGUMENT);
        when(firebaseMessaging.send(any())).thenThrow(mockEx);

        StepVerifier.create(service.sendToToken("token-1", new FcmNotification()))
                .assertNext(result -> {
                    assert !result.isSuccess();
                    assert result.getToken() == null;
                })
                .verifyComplete();
    }

    @Test
    void sendToUserDoesNotReturnFailedDeviceTokens() throws Exception {
        FirebaseMessagingException mockEx = mock(FirebaseMessagingException.class);
        when(mockEx.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNREGISTERED);
        when(deviceTokenService.getActiveTokens("user-1")).thenReturn(Mono.just(List.of("private-fcm-token")));
        when(firebaseMessaging.send(any())).thenThrow(mockEx);

        StepVerifier.create(service.sendToUser("user-1", new FcmNotification()))
                .assertNext(result -> {
                    assert result.getFailedTokens().isEmpty();
                    assert result.getToken() == null;
                })
                .verifyComplete();
    }
}
