package auto.parto.service.notification;

import auto.parto.dto.NotificationRequest;
import auto.parto.enums.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.UUID;

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
}
