package auto.parto.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record UpdateOfferStatusRequest(
        @NotBlank String status,
        @NotNull @PositiveOrZero Long expectedVersion) {
}
