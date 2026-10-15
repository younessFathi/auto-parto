package auto.parto.money;

import auto.parto.enums.Currency;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void parsesCurrenciesUsingTheirConfiguredMinorScale() {
        assertThat(Money.parse("12.34", Currency.MAD).minorUnits()).isEqualTo(1234);
        assertThat(Money.parse("1234", Currency.JPY).minorUnits()).isEqualTo(1234);
        assertThat(Money.parse("1.234", Currency.KWD).minorUnits()).isEqualTo(1234);
    }

    @Test
    void rejectsExcessPrecisionAndOverflow() {
        assertThatThrownBy(() -> Money.parse("1.001", Currency.MAD))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Money.fromMajor(new BigDecimal("92233720368547758.08"), Currency.MAD))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsCrossCurrencyArithmeticAndNegativeNonNegativeAmounts() {
        Money mad = Money.ofMinor(100, Currency.MAD);
        assertThatThrownBy(() -> mad.add(Money.ofMinor(1, Currency.USD)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Money.ofMinor(-1, Currency.MAD).requireNonNegative())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void detectsArithmeticOverflow() {
        assertThatThrownBy(() -> Money.ofMinor(Long.MAX_VALUE, Currency.MAD)
                .add(Money.ofMinor(1, Currency.MAD)))
                .isInstanceOf(ArithmeticException.class);
    }
}
