package auto.parto.persistence;

import java.time.Instant;
import java.util.UUID;

public record NotificationPreferencesRecord(UUID publicId, UUID userPublicId, boolean emailEnabled,
                                            boolean pushEnabled, boolean smsEnabled, boolean newOfferEnabled,
                                            boolean priceAlertEnabled, boolean searchMatchEnabled,
                                            boolean orderUpdateEnabled, boolean marketingEnabled,
                                            Instant createdAt, Instant updatedAt) {
}
