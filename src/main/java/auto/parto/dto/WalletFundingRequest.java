package auto.parto.dto;

import auto.parto.enums.Currency;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

/** An authorized administrator attests that the recorded manual funding is confirmed. */
public record WalletFundingRequest(
        @NotNull UUID beneficiaryUserPublicId,
        @NotNull @Positive Long amountMinor,
        @NotNull Currency currency,
        @NotNull @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._:/-]{0,254}") String fundingReference,
        @AssertTrue boolean confirmed) {
}
