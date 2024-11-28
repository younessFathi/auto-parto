package auto.parto.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
public class OtpRequestDto {
    private String phoneNumber;
    private String firstName;
    private String lastName;
    private String email;
}
