package auto.parto.dto;

import auto.parto.annotation.phoneNumber.ValidPhoneNumber;
import jakarta.validation.constraints.Size;

public record BuyerProfileUpdateRequest(
        @Size(max = 120) String firstName,
        @Size(max = 120) String lastName,
        @ValidPhoneNumber String phoneNumber,
        @Size(max = 2048) String pictureUrl) {
}
