package auto.parto.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SearchNotificationRequest {
    private String searchId;
    private String buyerId;
    private String partName;
    private String carMake;
    private String carModel;
    private String buyerLocation;
    private Double maxPrice;
    private String urgency; // "IMMEDIATE", "WITHIN_DAY", "FLEXIBLE"
}
