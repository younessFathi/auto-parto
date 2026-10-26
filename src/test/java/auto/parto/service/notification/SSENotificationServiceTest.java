package auto.parto.service.notification;

import auto.parto.dto.NotificationRequest;
import auto.parto.enums.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Sinks;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class SSENotificationServiceTest {

    private SSENotificationService service;

    @BeforeEach
    void setUp() {
        service = new SSENotificationService();
        ReflectionTestUtils.setField(service, "timeout", 100);
        ReflectionTestUtils.setField(service, "maxConnections", 1000);
        ReflectionTestUtils.setField(service, "bufferSize", 1000);
    }

    @Test
    void registerConnectionCreatesBoundedSink() {
        Sinks.Many<SseNotificationEvent> sink = service.registerConnection("user-1");
        assertThat(sink).isNotNull();
    }

    @Test
    void registerConnectionCompletesOldSinkOnReconnect() {
        Sinks.Many<SseNotificationEvent> first = service.registerConnection("user-1");
        Sinks.Many<SseNotificationEvent> second = service.registerConnection("user-1");

        NotificationRequest notification = new NotificationRequest(
                UUID.randomUUID().toString(), "user-1", NotificationType.NEW_SEARCH_POSTED, "test", null);
        assertThat(first.tryEmitNext(new SseNotificationEvent(UUID.randomUUID(), notification)))
                .isEqualTo(Sinks.EmitResult.FAIL_TERMINATED);
        assertThat(second.tryEmitNext(new SseNotificationEvent(UUID.randomUUID(), notification)).isSuccess()).isTrue();
    }

    @Test
    void replacementSucceedsWhenConnectionCapIsFull() {
        ReflectionTestUtils.setField(service, "maxConnections", 1);
        Sinks.Many<SseNotificationEvent> first = service.registerConnection("user-1");

        assertThat(service.registerConnection("user-2")).isNull();
        Sinks.Many<SseNotificationEvent> replacement = service.registerConnection("user-1");

        assertThat(replacement).isNotNull().isNotSameAs(first);
        assertThat(first.tryEmitNext(new SseNotificationEvent(UUID.randomUUID(), notification("user-1"))))
                .isEqualTo(Sinks.EmitResult.FAIL_TERMINATED);
        assertThat(service.getUserConnections("user-1")).isSameAs(replacement);
        assertThat(service.getConnectionStats().get("totalUsers")).isEqualTo(1);
    }

    @Test
    void concurrentDistinctRegistrationsNeverExceedConnectionCap() throws Exception {
        int maxConnections = 4;
        int registrations = 16;
        ReflectionTestUtils.setField(service, "maxConnections", maxConnections);
        ExecutorService executor = Executors.newFixedThreadPool(registrations);
        CountDownLatch ready = new CountDownLatch(registrations);
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<Future<Sinks.Many<SseNotificationEvent>>> attempts =
                    java.util.stream.IntStream.range(0, registrations)
                            .mapToObj(index -> executor.submit(() -> {
                                ready.countDown();
                                if (!start.await(5, TimeUnit.SECONDS)) {
                                    throw new IllegalStateException("registration start timed out");
                                }
                                return service.registerConnection("user-" + index);
                            }))
                            .toList();

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Sinks.Many<SseNotificationEvent>> results = attempts.stream()
                    .map(attempt -> {
                        try {
                            return attempt.get(5, TimeUnit.SECONDS);
                        } catch (Exception exception) {
                            throw new AssertionError("registration did not complete", exception);
                        }
                    })
                    .toList();

            assertThat(results.stream().filter(Objects::nonNull)).hasSize(maxConnections);
            assertThat(service.getConnectionStats().get("totalUsers")).isEqualTo(maxConnections);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void unregisterConnectionRemovesSink() {
        service.registerConnection("user-1");
        service.unregisterConnection("user-1");

        assertThat(service.hasActiveConnection("user-1")).isFalse();
    }

    @Test
    void sendViaSSEReturnsFalseWhenNoConnection() {
        NotificationRequest notification = new NotificationRequest(
                UUID.randomUUID().toString(), "user-1", NotificationType.NEW_SEARCH_POSTED, "test message", null
        );
        boolean result = service.sendViaSSE("user-1", notification);
        assertThat(result).isFalse();
    }

    @Test
    void boundedBufferReportsOverflowWithoutThrowing() {
        ReflectionTestUtils.setField(service, "bufferSize", 1);
        service.registerConnection("user-1");
        NotificationRequest notification = new NotificationRequest(
                UUID.randomUUID().toString(), "user-1", NotificationType.NEW_SEARCH_POSTED, "test message", null);

        assertThat(service.sendViaSSE("user-1", UUID.randomUUID(), notification)).isTrue();
        assertThat(service.sendViaSSE("user-1", UUID.randomUUID(), notification)).isFalse();
    }

    private static NotificationRequest notification(String userId) {
        return new NotificationRequest(UUID.randomUUID().toString(), userId,
                NotificationType.NEW_SEARCH_POSTED, "test", null);
    }
}
