package auto.parto.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SellerResponseRequest {
    private String responseId;
    private String searchId;
    private String buyerId;
    private String sellerId;
    private String sellerName;
    private String partName;
}
