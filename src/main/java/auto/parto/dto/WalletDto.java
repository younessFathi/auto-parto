package auto.parto.dto;

import auto.parto.enums.Currency;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WalletDto {
    private String id;
    private Long balanceMinor;
    private Currency currency;

    public void addFunds(long amountMinor) {
        if (balanceMinor == null) balanceMinor = 0L;
        balanceMinor = Math.addExact(balanceMinor, amountMinor);
    }
}
