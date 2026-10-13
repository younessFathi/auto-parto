package auto.parto.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

public record CreateOfferRequest(
        @NotNull @PositiveOrZero Long amountMinor,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
        boolean negotiable,
        @NotNull @PositiveOrZero Long deliveryAmountMinor,
        String deliveryMode) {
}
