package auto.parto.service.notification;


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
public class FcmNotification {

    private String title;
    private String body;
    private String buyerProfileId;

    @Builder.Default
    private Map<String, String> data = new HashMap<>();

    @Builder.Default
    private NotificationPriority priority = NotificationPriority.NORMAL;

    private String imageUrl; // Optional: For rich notifications
    private String clickAction; // Optional: Custom action on click
    private String sound; // Optional: Custom sound
    private String channelId; // Optional: Android channel ID

    public enum NotificationPriority {
        NORMAL,
        HIGH
    }

    // Helper method for car part search notifications
    public static FcmNotification newSearchMatch(String partName, String location, String searchId , String buyerProfileId) {
        return FcmNotification.builder()
                .title("🔍 New Car Part Search")
                .body(String.format("Buyer in %s needs %s", location, partName))
                .priority(NotificationPriority.HIGH)
                .data(Map.of(
                        "type", "NEW_SEARCH_MATCH",
                        "searchId", searchId,
                        "partName", partName,
                        "location", location,
                        "buyer-profile", buyerProfileId,
                        "timestamp", String.valueOf(System.currentTimeMillis())
                ))
                .channelId("urgent-searches") // Android channel
                .build();
    }

    // Helper method for seller response notifications
    public static FcmNotification sellerResponse(String sellerName, String partName, String responseId) {
        return FcmNotification.builder()
                .title("💬 " + sellerName + " responded")
                .body("Check their quote for " + partName)
                .priority(NotificationPriority.NORMAL)
                .data(Map.of(
                        "type", "SELLER_RESPONSE",
                        "responseId", responseId,
                        "sellerName", sellerName,
                        "partName", partName
                ))
                .channelId("seller-responses")
                .build();
    }

    // Helper method for urgent notifications
    public static FcmNotification urgentSearch(String partName, String location) {
        return FcmNotification.builder()
                .title("🔥 URGENT: " + partName + " needed!")
                .body("Immediate requirement in " + location)
                .priority(NotificationPriority.HIGH)
                .data(Map.of(
                        "type", "URGENT_SEARCH",
                        "partName", partName,
                        "location", location,
                        "urgency", "IMMEDIATE"
                ))
                .channelId("urgent-searches")
                .build();
    }
}