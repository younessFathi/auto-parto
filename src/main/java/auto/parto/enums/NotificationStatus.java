package auto.parto.enums;

import lombok.Getter;

@Getter
public enum NotificationStatus {
    // Delivery states
    PENDING("PENDING", "Notification is queued for delivery"),
    DELIVERING("DELIVERING", "Currently attempting delivery"),
    DELIVERED("DELIVERED", "Successfully delivered to client"),
    FAILED("FAILED", "Delivery attempt failed"),

    // Storage states
    STORED("STORED", "Stored for later delivery"),
    ARCHIVED("ARCHIVED", "Archived after successful delivery"),
    EXPIRED("EXPIRED", "Expired before delivery"),

    // User states
    READ("READ", "User has read the notification"),
    UNREAD("UNREAD", "User hasn't read the notification"),
    DISMISSED("DISMISSED", "User dismissed the notification"),

    // System states
    CANCELLED("CANCELLED", "Cancelled before delivery"),
    RETRYING("RETRYING", "Scheduled for retry");

    private final String code;
    private final String description;

    NotificationStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public boolean isFinalState() {
        return this == DELIVERED ||
                this == READ ||
                this == ARCHIVED ||
                this == EXPIRED ||
                this == CANCELLED;
    }

    public boolean requiresRetry() {
        return this == FAILED || this == RETRYING;
    }

    public boolean isActive() {
        return this == PENDING ||
                this == DELIVERING ||
                this == RETRYING ||
                this == UNREAD;
    }

    public static NotificationStatus fromCode(String code) {
        for (NotificationStatus status : values()) {
            if (status.code.equalsIgnoreCase(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown status code: " + code);
    }
}
