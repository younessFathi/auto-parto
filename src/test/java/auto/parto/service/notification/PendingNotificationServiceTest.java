package auto.parto.service.notification;

import auto.parto.dto.NotificationRequest;
import auto.parto.enums.NotificationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PendingNotificationServiceTest {

    @Mock
    private HybridNotificationStore store;

    @Test
    void propagatesFailedDurableStoreResult() {
        PendingNotificationService service = new PendingNotificationService(store);
        NotificationRequest request = new NotificationRequest("request-1", "00000000-0000-0000-0000-000000000001",
                NotificationType.NEW_SEARCH_POSTED, "safe", 1L);
        when(store.storePending(any())).thenReturn(Mono.just(false));

        StepVerifier.create(service.addPendingNotification(request))
                .expectNext(false)
                .verifyComplete();
        verify(store).storePending(request);
    }
}
