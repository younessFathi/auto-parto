package auto.parto.service.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class BatchFcmResult {
    private int totalUsers;
    private int successfulUsers;
    private int failedUsers;
    private List<UserDeliveryResult> results;

    @Data
    @Builder
    @AllArgsConstructor
    public static class UserDeliveryResult {
        private String userId;
        private FcmDeliveryResult fcmDeliveryResult;
        private String error;
    }
}
