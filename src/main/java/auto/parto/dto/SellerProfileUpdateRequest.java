package auto.parto.dto;

import auto.parto.annotation.phoneNumber.ValidPhoneNumber;
import jakarta.validation.constraints.Size;

public record SellerProfileUpdateRequest(
        @Size(max = 120) String firstName,
        @Size(max = 120) String lastName,
        @ValidPhoneNumber String phoneNumber,
        @Size(max = 64) String partType,
        @Size(max = 1000) String description) {
}
