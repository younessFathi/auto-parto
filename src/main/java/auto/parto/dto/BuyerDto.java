package auto.parto.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.auth.oauth2.JwtProvider;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class BuyerDto implements OtpVerifiable , JwtKeyProvider {
    private String id;
    private String clerkId;
    private String firstName;
    private String lastName;
    private String email;
    private String phoneNumber;
    private String picture;
    private OtpDetailsDto otpDetailsDto;
    @JsonIgnore
    private String jwtKey;
    private Boolean isActive;
    private LocalDateTime createAt;
    private LocalDateTime updatedAt;
}
