package auto.parto.dto;

import auto.parto.annotation.phoneNumber.ValidPhoneNumber;
import auto.parto.enums.CarPartType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SellerDto implements OtpVerifiable , JwtKeyProvider {
    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private String id;
    @NotBlank(message = "Name is required")
    private String name;
    @NotBlank(message = "Email is required")
    @Email
    private String email;
    @NotBlank(message = "Phone number is required")
    @ValidPhoneNumber
    private String phoneNumber;
    private AddressDto addressDto;
    @NotNull(message = "Selling types are required")
    private CarPartType carPartType;
    private String description;
    private LocalDateTime createAt;
    private LocalDateTime modifiedAt;
    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private WalletDto walletDto;
    private Boolean isActive;
    private OtpDetailsDto otpDetailsDto;
    @JsonIgnore
    private String jwtKey;
}
