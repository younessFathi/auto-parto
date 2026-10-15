package auto.parto.model;

import auto.parto.enums.Currency;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@Builder
public class Wallet {
    @Id
    private String id;
    private long balanceMinor;
    private Currency currency;
    public Wallet(String id, long balanceMinor, Currency currency) {
        this.id = id;
        this.balanceMinor = balanceMinor;
        this.currency = currency;
    }
    public void addFunds(long amountMinor) {
        this.balanceMinor = Math.addExact(this.balanceMinor, amountMinor);
    }
}
