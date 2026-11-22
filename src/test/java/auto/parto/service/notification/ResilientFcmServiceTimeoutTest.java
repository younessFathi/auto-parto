package auto.parto.service.notification;

import com.google.firebase.messaging.FirebaseMessaging;
import auto.parto.config.ProviderCallTelemetry;
import com.google.api.core.SettableApiFuture;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResilientFcmServiceTimeoutTest {

    @Mock
    private FirebaseMessaging firebaseMessaging;
    @Mock
    private DeviceTokenService deviceTokenService;
    @Mock private NotificationPreferenceFilter preferences;
    private ResilientFcmService service;
    private CircuitBreaker circuitBreaker;
    private SimpleMeterRegistry registry;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(deviceTokenService.activeOwner(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Mono.just("user-1"));
        org.mockito.Mockito.lenient().when(preferences.canSend(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("push"), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Mono.just(true));
        circuitBreaker = CircuitBreaker.ofDefaults("fcm-test");
        registry = new SimpleMeterRegistry();
        service = new ResilientFcmService(firebaseMessaging, deviceTokenService, preferences, circuitBreaker,
                new ProviderCallTelemetry(registry), Duration.ofMillis(50), Duration.ofMillis(1), 0, 1, 1);
    }

    @Test
    void sendToTokenTimeoutCancelsAsyncCallAndReturnsSafeFailure() throws Exception {
        SettableApiFuture<String> pending = SettableApiFuture.create();
        when(firebaseMessaging.sendAsync(any())).thenReturn(pending);

        StepVerifier.create(service.sendToToken("token-1", FcmNotification.sellerResponse("seller", "part", "response")))
                .assertNext(result -> org.assertj.core.api.Assertions.assertThat(result.isSuccess()).isFalse())
                .verifyComplete();

        org.assertj.core.api.Assertions.assertThat(pending.isCancelled()).isTrue();
        org.assertj.core.api.Assertions.assertThat(registry.get("auto.parto.provider.calls")
                        .tag("provider", "firebase").tag("outcome", "timeout").timer().count())
                .isEqualTo(1);
    }

    @Test
    void rejectsProviderCallWhenGlobalInFlightLimitIsFull() throws Exception {
        SettableApiFuture<String> pending = SettableApiFuture.create();
        CountDownLatch started = new CountDownLatch(1);
        when(firebaseMessaging.sendAsync(any())).thenAnswer(invocation -> {
            started.countDown();
            return pending;
        });

        reactor.core.Disposable first = service.sendToToken("token-1", FcmNotification.sellerResponse("seller", "part", "response")).subscribe();
        org.assertj.core.api.Assertions.assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();

        StepVerifier.create(service.sendToToken("token-2", FcmNotification.sellerResponse("seller", "part", "response")))
                .assertNext(result -> org.assertj.core.api.Assertions.assertThat(result.isSuccess()).isFalse())
                .verifyComplete();

        org.assertj.core.api.Assertions.assertThat(registry.get("auto.parto.provider.calls")
                        .tag("provider", "firebase").tag("outcome", "saturated").timer().count())
                .isEqualTo(1);
        first.dispose();
        org.assertj.core.api.Assertions.assertThat(pending.isCancelled()).isTrue();
    }
}
