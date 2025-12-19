package auto.parto.dto;

import auto.parto.enums.NotificationType;

public record NotificationRequest (
        NotificationType type,
        String message,
        Long sysTime
) {}