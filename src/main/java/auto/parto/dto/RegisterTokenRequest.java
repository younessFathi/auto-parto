package auto.parto.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegisterTokenRequest {

    @NotBlank(message = "FCM token is required")
    private String deviceToken;

    @NotBlank(message = "Platform is required")
    private String platform; // "android", "ios", "web"

    private String userId;

    private String appVersion;

    private String deviceModel;

    private String osVersion;
}
