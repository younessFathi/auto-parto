package auto.parto.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class UrgentNotificationRequest {
    private List<String> userIds;
    private String title;
    private String message;
    private Map<String, String> metadata;
}
