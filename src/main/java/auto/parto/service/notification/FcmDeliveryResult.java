package auto.parto.service.notification;


import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FcmDeliveryResult {

    private String userId;
    private boolean success;

    @Builder.Default
    private String status = "PENDING"; // PENDING, SENT, DELIVERED, FAILED

    private String messageId; // FCM message ID
    private String error; // Error message if failed

    @Builder.Default
    private List<String> failedTokens = new ArrayList<>();

    private int totalTokens;
    private int successfulTokens;
    private String token;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    // Factory methods
    public static FcmDeliveryResult success(String userId, String messageId) {
        return FcmDeliveryResult.builder()
                .userId(userId)
                .success(true)
                .status("DELIVERED")
                .messageId(messageId)
                .totalTokens(1)
                .successfulTokens(1)
                .build();
    }

    public static FcmDeliveryResult failed(String userId, String error) {
        return FcmDeliveryResult.builder()
                .userId(userId)
                .success(false)
                .status("FAILED")
                .error(error)
                .totalTokens(0)
                .successfulTokens(0)
                .build();
    }

    public static FcmDeliveryResult pending(String userId) {
        return FcmDeliveryResult.builder()
                .userId(userId)
                .success(false)
                .status("PENDING")
                .totalTokens(0)
                .successfulTokens(0)
                .build();
    }

    public static FcmDeliveryResult partial(String userId, int total, int successful, List<String> failed) {
        return FcmDeliveryResult.builder()
                .userId(userId)
                .success(successful > 0)
                .status(successful > 0 ? "PARTIAL" : "FAILED")
                .totalTokens(total)
                .successfulTokens(successful)
                .failedTokens(failed != null ? failed : new ArrayList<>())
                .build();
    }

    public static FcmDeliveryResult noTokens(String userId) {
        return FcmDeliveryResult.builder()
                .userId(userId)
                .success(false)
                .status("NO_TOKENS")
                .error("User has no registered device tokens")
                .totalTokens(0)
                .successfulTokens(0)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
