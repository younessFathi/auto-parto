package auto.parto.dto;

import auto.parto.annotation.phoneNumber.ValidPhoneNumber;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

@Data
@AllArgsConstructor
@Builder
public class OtpRequestDto {
    @NotNull
    @NotBlank
    @ValidPhoneNumber
    private String phoneNumber;
    @Length(min = 3, max = 15)
    private String firstName;
    @Length(min = 3, max = 15)
    private String lastName;
    @Email
    private String email;
}
