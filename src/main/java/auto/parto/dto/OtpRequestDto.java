package auto.parto.dto;

import auto.parto.annotation.phoneNumber.ValidPhoneNumber;
import auto.parto.enums.CustomerType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Length;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OtpRequestDto {
    //confirmed via sms
    private String phoneNumber;
    @Length(min = 3, max = 15)
    private String firstName;
    @Length(min = 3, max = 15)
    private String lastName;
    @Email
    private String email;

    @NotNull(message = "Customer type is required BUYER / SELLER")
    private CustomerType customerType;
}

