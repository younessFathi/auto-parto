package auto.parto.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "notification_preferences")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPreferences {
    @Id
    private String id;
    private String userId;
    private boolean emailEnabled;
    private boolean pushEnabled;
    private boolean smsEnabled;
    private boolean newOfferEnabled;
    private boolean priceAlertEnabled;
    private boolean searchMatchEnabled;
    private boolean orderUpdateEnabled;
    private boolean marketingEnabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static NotificationPreferences createDefault(String userId) {
        return NotificationPreferences.builder()
                .userId(userId)
                .emailEnabled(true)
                .pushEnabled(true)
                .smsEnabled(false)
                .newOfferEnabled(true)
                .priceAlertEnabled(true)
                .searchMatchEnabled(true)
                .orderUpdateEnabled(true)
                .marketingEnabled(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}