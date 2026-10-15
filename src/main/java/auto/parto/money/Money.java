package auto.parto.money;

import auto.parto.enums.Currency;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** A currency amount stored as a signed 64-bit integer in minor units. */
public record Money(long minorUnits, Currency currency) {

    public Money {
        Objects.requireNonNull(currency, "currency is required");
    }

    public static Money ofMinor(long minorUnits, Currency currency) {
        return new Money(minorUnits, currency);
    }

    public static Money parse(String amount, Currency currency) {
        if (amount == null || amount.isBlank()) {
            throw new IllegalArgumentException("amount is required");
        }
        try {
            BigDecimal decimal = new BigDecimal(amount.trim());
            return new Money(decimal.movePointRight(currency.minorUnitScale())
                    .setScale(0, RoundingMode.UNNECESSARY).longValueExact(), currency);
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalArgumentException("amount has invalid precision or range", exception);
        }
    }

    public static Money fromMajor(BigDecimal amount, Currency currency) {
        if (amount == null) {
            throw new IllegalArgumentException("amount is required");
        }
        return parse(amount.toPlainString(), currency);
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(Math.addExact(minorUnits, other.minorUnits), currency);
    }

    public Money subtract(Money other) {
        requireSameCurrency(other);
        return new Money(Math.subtractExact(minorUnits, other.minorUnits), currency);
    }

    public Money negate() {
        return new Money(Math.negateExact(minorUnits), currency);
    }

    public Money requireNonNegative() {
        if (minorUnits < 0) {
            throw new IllegalArgumentException("amount must be non-negative");
        }
        return this;
    }

    public BigDecimal toMajor() {
        return BigDecimal.valueOf(minorUnits, currency.minorUnitScale());
    }

    private void requireSameCurrency(Money other) {
        if (other == null || currency != other.currency) {
            throw new IllegalArgumentException("money currencies must match");
        }
    }
}
