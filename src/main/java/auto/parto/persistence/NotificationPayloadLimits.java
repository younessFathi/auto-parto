package auto.parto.persistence;

import java.nio.charset.StandardCharsets;

public final class NotificationPayloadLimits {

    public static final int MAX_SERIALIZED_BYTES = 128 * 1024;

    private NotificationPayloadLimits() {
    }

    public static void requireSize(String payloadJson) {
        if (payloadJson.getBytes(StandardCharsets.UTF_8).length > MAX_SERIALIZED_BYTES) {
            throw new IllegalArgumentException("notification payload exceeds the bounded size");
        }
    }
}
