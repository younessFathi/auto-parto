package auto.parto.dto;

import auto.parto.annotation.otp.ValidOtp;
import auto.parto.enums.CustomerType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ValidateOtpRequest {
    @ValidOtp
    private String otp;
    @Email
    private String email;
    @NotNull
    @NotBlank
    private CustomerType customerType;
}
