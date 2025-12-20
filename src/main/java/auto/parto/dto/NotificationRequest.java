package auto.parto.dto;

import auto.parto.enums.NotificationType;

public record NotificationRequest (
        String id,
        String userId,
        NotificationType type,
        String message,
        Long sysTime
) {}