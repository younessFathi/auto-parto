package auto.parto.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPreferencesDto {
    private String userId;
    private boolean emailEnabled;
    private boolean pushEnabled;
    private boolean smsEnabled;
    private boolean newOfferEnabled;
    private boolean priceAlertEnabled;
    private boolean searchMatchEnabled;
    private boolean orderUpdateEnabled;
    private boolean marketingEnabled;
}