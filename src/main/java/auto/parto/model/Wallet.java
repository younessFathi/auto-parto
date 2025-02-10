package auto.parto.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;

@Document(collection = "addresses")
@Data
@NoArgsConstructor
@Builder
public class Wallet {
    private String id;
    private BigDecimal balance;
    private String currency;
    public Wallet(String id, BigDecimal balance, String currency) {
        this.id = id;
        this.balance = balance;
        this.currency = currency;
    }
    public void addFunds(BigDecimal amount) {
        this.balance = this.balance.add(amount);
    }
}
