package auto.parto.service.notification;

import auto.parto.service.NotificationPreferencesService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationPreferenceFilterTest {

    @Mock
    private NotificationPreferencesService preferencesService;

    @Test
    void canSendPushNotification_returnsTrue_whenEnabled() {
        NotificationPreferenceFilter filter = new NotificationPreferenceFilter(preferencesService);

        when(preferencesService.isChannelEnabled("user-123", "push")).thenReturn(Mono.just(true));

        StepVerifier.create(filter.canSendPushNotification("user-123"))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void canSendPushNotification_returnsFalse_whenDisabled() {
        NotificationPreferenceFilter filter = new NotificationPreferenceFilter(preferencesService);

        when(preferencesService.isChannelEnabled("user-123", "push")).thenReturn(Mono.just(false));

        StepVerifier.create(filter.canSendPushNotification("user-123"))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void canSendEmailNotification_returnsTrue_whenEnabled() {
        NotificationPreferenceFilter filter = new NotificationPreferenceFilter(preferencesService);

        when(preferencesService.isChannelEnabled("user-123", "email")).thenReturn(Mono.just(true));

        StepVerifier.create(filter.canSendEmailNotification("user-123"))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void canSendEmailNotification_returnsFalse_whenDisabled() {
        NotificationPreferenceFilter filter = new NotificationPreferenceFilter(preferencesService);

        when(preferencesService.isChannelEnabled("user-123", "email")).thenReturn(Mono.just(false));

        StepVerifier.create(filter.canSendEmailNotification("user-123"))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void canSendSmsNotification_returnsTrue_whenEnabled() {
        NotificationPreferenceFilter filter = new NotificationPreferenceFilter(preferencesService);

        when(preferencesService.isChannelEnabled("user-123", "sms")).thenReturn(Mono.just(true));

        StepVerifier.create(filter.canSendSmsNotification("user-123"))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void canSendSmsNotification_returnsFalse_whenDisabled() {
        NotificationPreferenceFilter filter = new NotificationPreferenceFilter(preferencesService);

        when(preferencesService.isChannelEnabled("user-123", "sms")).thenReturn(Mono.just(false));

        StepVerifier.create(filter.canSendSmsNotification("user-123"))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void canSendNotificationType_returnsTrue_whenEnabled() {
        NotificationPreferenceFilter filter = new NotificationPreferenceFilter(preferencesService);

        when(preferencesService.isNotificationTypeEnabled("user-123", "newoffer")).thenReturn(Mono.just(true));

        StepVerifier.create(filter.canSendNotificationType("user-123", "newoffer"))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void canSendNotificationType_returnsFalse_whenDisabled() {
        NotificationPreferenceFilter filter = new NotificationPreferenceFilter(preferencesService);

        when(preferencesService.isNotificationTypeEnabled("user-123", "pricealert")).thenReturn(Mono.just(false));

        StepVerifier.create(filter.canSendNotificationType("user-123", "pricealert"))
                .expectNext(false)
                .verifyComplete();
    }
}