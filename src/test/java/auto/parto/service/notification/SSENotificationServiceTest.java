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
        Sinks.Many<NotificationRequest> sink = service.registerConnection("user-1");
        assertThat(sink).isNotNull();
    }

    @Test
    void registerConnectionCompletesOldSinkOnReconnect() {
        Sinks.Many<NotificationRequest> first = service.registerConnection("user-1");
        Sinks.Many<NotificationRequest> second = service.registerConnection("user-1");

        assertThat(first.tryEmitNext(new NotificationRequest(
                UUID.randomUUID().toString(), "user-1", NotificationType.NEW_SEARCH_POSTED, "test", null
        ))).isEqualTo(Sinks.EmitResult.FAIL_TERMINATED);
        assertThat(second.tryEmitNext(new NotificationRequest(
                UUID.randomUUID().toString(), "user-1", NotificationType.NEW_SEARCH_POSTED, "test", null
        )).isSuccess()).isTrue();
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
}
