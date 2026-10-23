package auto.parto.service.notification;

import auto.parto.dto.NotificationRequest;

import java.util.UUID;

/** Transport envelope carrying the durable notification public UUID. */
public record SseNotificationEvent(UUID eventId, NotificationRequest notification) {
}
