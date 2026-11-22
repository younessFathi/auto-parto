package auto.parto.service.notification;

import auto.parto.service.NotificationPreferencesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationPreferenceFilter {

    private final NotificationPreferencesService preferencesService;

    public Mono<Boolean> canSend(String userId, String channel, String type) {
        return Mono.defer(() -> preferencesService.canDeliver(userId, channel, type))
                .timeout(java.time.Duration.ofSeconds(5));
    }

    public Mono<Boolean> canSendPushNotification(String userId) {
        return preferencesService.isChannelEnabled(userId, "push");
    }

    public Mono<Boolean> canSendEmailNotification(String userId) {
        return preferencesService.isChannelEnabled(userId, "email");
    }

    public Mono<Boolean> canSendSmsNotification(String userId) {
        return preferencesService.isChannelEnabled(userId, "sms");
    }

    public Mono<Boolean> canSendNotificationType(String userId, String notificationType) {
        return preferencesService.isNotificationTypeEnabled(userId, notificationType);
    }
}