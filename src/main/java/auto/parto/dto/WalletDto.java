package auto.parto.dto;

import auto.parto.enums.Currency;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WalletDto {
    private String id;
    private BigDecimal balance;
    private Currency currency;

    public void addFunds(BigDecimal amount) {
        this.balance = this.balance.add(amount);
    }
}
