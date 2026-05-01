package auto.parto.model;

import auto.parto.enums.Currency;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@Builder
public class Wallet {
    @Id
    private String id;
    private BigDecimal balance;
    private Currency currency;
    public Wallet(String id, BigDecimal balance, Currency currency) {
        this.id = id;
        this.balance = balance;
        this.currency = currency;
    }
    public void addFunds(BigDecimal amount) {
        this.balance = this.balance.add(amount);
    }
}
