package auto.parto.service.notification;

import com.google.firebase.messaging.FirebaseMessaging;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResilientFcmServiceTimeoutTest {

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
        ReflectionTestUtils.setField(service, "fcmTimeout", Duration.ofMillis(50));
    }

    @Test
    void sendToTokenTimeoutTriggersRetryAndFallback() throws Exception {
        when(firebaseMessaging.send(any())).thenAnswer(invocation -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "msg-id";
        });

        StepVerifier.create(service.sendToToken("token-1", new FcmNotification()))
                .assertNext(result -> {
                    assert !result.isSuccess();
                })
                .verifyComplete();
    }
}
