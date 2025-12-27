package auto.parto.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.util.Map;
import java.util.HashMap;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FcmRequest {

    @NotBlank(message = "User ID is required")
    private String userId;

    @NotBlank(message = "Notification title is required")
    private String title;

    @NotBlank(message = "Notification body is required")
    private String body;

    @Builder.Default
    private Map<String, String> data = new HashMap<>();

    @Builder.Default
    private boolean urgent = false;

    private String imageUrl; // For rich notifications
    private String clickAction; // Custom action when notification is clicked

    // Helper method for car part search
    public static FcmRequest createSearchNotification(String userId, String partName, String location, String searchId) {
        return FcmRequest.builder()
                .userId(userId)
                .title("🔍 New Search: " + partName)
                .body("Buyer in " + location + " needs " + partName)
                .urgent(true)
                .data(Map.of(
                        "type", "NEW_SEARCH_MATCH",
                        "searchId", searchId,
                        "partName", partName,
                        "location", location
                ))
                .build();
    }

    // Helper method for seller response
    public static FcmRequest createSellerResponse(String userId, String sellerName, String partName, String responseId) {
        return FcmRequest.builder()
                .userId(userId)
                .title("💬 " + sellerName + " responded")
                .body("Check their quote for " + partName)
                .urgent(false)
                .data(Map.of(
                        "type", "SELLER_RESPONSE",
                        "responseId", responseId,
                        "sellerName", sellerName,
                        "partName", partName
                ))
                .build();
    }

    public static FcmRequest createAllSellersNotification(String partName, String location, String searchId) {
        return FcmRequest.builder()
                .userId("/topics/sellers") // Target the topic
                .title("📢 New Request: " + partName)
                .body("A buyer in " + location + " is looking for " + partName)
                .urgent(true)
                .data(Map.of(
                        "type", "NEW_SEARCH_BROADCAST",
                        "searchId", searchId,
                        "partName", partName
                ))
                .build();
    }
}
