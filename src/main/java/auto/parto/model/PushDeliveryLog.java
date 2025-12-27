package auto.parto.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "push_delivery_logs")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PushDeliveryLog {
    @Id
    private String id;
    private String userId;
    private String notificationId;
    private DeliveryStatus status; // SENT, DELIVERED, FAILED, CLICKED
    private String deviceToken;
    private String failureReason;
    private LocalDateTime sentAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime clickedAt;

    public enum DeliveryStatus {
        PENDING, SENT, DELIVERED, FAILED, CLICKED
    }
}
