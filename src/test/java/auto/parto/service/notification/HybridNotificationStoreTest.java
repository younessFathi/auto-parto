package auto.parto.service.notification;

import auto.parto.dto.NotificationRequest;
import auto.parto.enums.NotificationType;
import auto.parto.persistence.NotificationRecord;
import auto.parto.persistence.NotificationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HybridNotificationStoreTest {

    @Mock
    private RedisNotificationStore redisStore;
    @Mock
    private NotificationRepository repository;
    @Mock
    private ObjectProvider<NotificationRepository> repositories;
    @Mock private NotificationPreferenceFilter preferences;

    @Test
    void reportsFalseWhenPostgresqlPersistenceFails() {
        HybridNotificationStore store = store();
        NotificationRequest request = request();
        when(repository.enqueue(any(), any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(store.storePending(request))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void keepsDurableSuccessWhenRedisProjectionFails() {
        HybridNotificationStore store = store();
        NotificationRequest request = request();
        UUID publicId = UUID.randomUUID();
        when(repository.enqueue(any(), any(), any())).thenReturn(Mono.just(new NotificationRecord(publicId,
                UUID.fromString(request.userId()), "NEW_SEARCH_POSTED", "{}", "PENDING", Instant.now(), Instant.now())));
        when(redisStore.storePending(request.userId(), request)).thenReturn(Mono.error(new IllegalStateException("redis")));

        StepVerifier.create(store.storePending(request))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void replayPreservesCursorOrderAcrossAsynchronousPreferenceReads() throws Exception {
        HybridNotificationStore store = store();
        NotificationRequest first = request();
        NotificationRequest second = new NotificationRequest("request-2", first.userId(), first.type(), "safe", 2L);
        UUID cursor = UUID.randomUUID(), firstId = UUID.randomUUID(), secondId = UUID.randomUUID();
        var mapper = new ObjectMapper();
        when(repository.findForRecipientAfter(UUID.fromString(first.userId()), cursor, 100))
                .thenReturn(reactor.core.publisher.Flux.just(
                        new NotificationRecord(firstId, UUID.fromString(first.userId()), first.type().name(), mapper.writeValueAsString(first), "DISPATCHED", Instant.now(), Instant.now()),
                        new NotificationRecord(secondId, UUID.fromString(first.userId()), second.type().name(), mapper.writeValueAsString(second), "DISPATCHED", Instant.now(), Instant.now())));
        when(preferences.canSend(first.userId(), "push", first.type().name()))
                .thenReturn(Mono.delay(java.time.Duration.ofMillis(30)).map(ignored -> true), Mono.just(true));
        StepVerifier.create(store.replayNotifications(first.userId(), cursor, 100).map(HybridNotificationStore.ClaimedNotification::publicId))
                .expectNext(firstId, secondId).verifyComplete();
    }

    private HybridNotificationStore store() {
        when(repositories.getIfAvailable()).thenReturn(repository);
        return new HybridNotificationStore(redisStore, repositories, new ObjectMapper(), preferences);
    }

    private static NotificationRequest request() {
        return new NotificationRequest("request-1", "00000000-0000-0000-0000-000000000001",
                NotificationType.NEW_SEARCH_POSTED, "safe", 1L);
    }
}
