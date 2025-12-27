package auto.parto.service.notification;

import auto.parto.dto.FcmRequest;
import auto.parto.dto.SearchNotification;

import java.util.HashMap;
import java.util.Map;

public class PushNotificationFactory {

    public static FcmRequest createCarPartSearchNotification(
            String deviceToken,
            SearchNotification search) {

        Map<String, String> data = new HashMap<>();
        data.put("type", "NEW_SEARCH_MATCH");
        data.put("searchId", search.getId());
        data.put("partName", search.getPartName());
        data.put("action", "VIEW_SEARCH");

        return FcmRequest.builder()
                .userId(deviceToken)
                .title("🔍 New Car Part Search")
                .body(String.format("Buyer needs %s in %s", search.getPartName(), search.getBuyerLocation()))
                .urgent(true)
                .data(data)
                .build();
    }
}