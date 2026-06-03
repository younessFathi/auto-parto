package auto.parto.service.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveZSetOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.ScheduledExecutorService;

import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
class PushNotificationQueueTest {

    @Mock
    private ReactiveRedisTemplate<String, String> redisTemplate;
    @Mock
    private ReactiveZSetOperations<String, String> zSetOps;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private ScheduledExecutorService retryExecutor;
    @Mock
    private ResilientFcmService resilientFcmService;

    private PushNotificationQueue queue;

    @BeforeEach
    void setUp() {
        queue = new PushNotificationQueue(redisTemplate, objectMapper, retryExecutor);
        ReflectionTestUtils.setField(queue, "resilientFcmService", resilientFcmService);
    }

    @Test
    void attemptRedeliveryCallsFcmServiceAndReturnsTrueOnSuccess() {
        FcmNotification notification = FcmNotification.builder()
                .title("Test")
                .body("Test body")
                .build();
        PushNotificationQueue.QueuedNotification queued = PushNotificationQueue.QueuedNotification.builder()
                .id("notif-1")
                .userId("user-1")
                .notification(notification)
                .retryCount(0)
                .build();

        when(resilientFcmService.sendToUser(eq("user-1"), any(FcmNotification.class)))
                .thenReturn(Mono.just(FcmDeliveryResult.success("token-1", "msg-1")));

        StepVerifier.create(queue.attemptRedelivery(queued))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void attemptRedeliverySchedulesNextRetryOnFailure() throws Exception {
        FcmNotification notification = FcmNotification.builder()
                .title("Test")
                .body("Test body")
                .build();
        PushNotificationQueue.QueuedNotification queued = PushNotificationQueue.QueuedNotification.builder()
                .id("notif-1")
                .userId("user-1")
                .notification(notification)
                .retryCount(0)
                .build();

        when(resilientFcmService.sendToUser(eq("user-1"), any(FcmNotification.class)))
                .thenReturn(Mono.just(FcmDeliveryResult.failed("token-1", "error")));
        doReturn("{}").when(objectMapper).writeValueAsString(any());
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        when(zSetOps.add(anyString(), anyString(), anyDouble())).thenReturn(Mono.just(true));

        StepVerifier.create(queue.attemptRedelivery(queued))
                .expectNext(true)
                .verifyComplete();
    }
}
