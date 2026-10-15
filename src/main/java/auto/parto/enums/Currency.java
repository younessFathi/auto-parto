package auto.parto.enums;

public enum Currency {
    USD("United States Dollar", 2), MAD("MAD", 2), EUR("Euro", 2), GBP("British Pound Sterling", 2),
    JPY("Japanese Yen", 0), CNY("Chinese Yuan", 2), INR("Indian Rupee", 2), AUD("Australian Dollar", 2),
    CAD("Canadian Dollar", 2), CHF("Swiss Franc", 2), SEK("Swedish Krona", 2), NZD("New Zealand Dollar", 2),
    MXN("Mexican Peso", 2), SGD("Singapore Dollar", 2), HKD("Hong Kong Dollar", 2), NOK("Norwegian Krone", 2),
    KRW("South Korean Won", 0), TRY("Turkish Lira", 2), RUB("Russian Ruble", 2), BRL("Brazilian Real", 2),
    ZAR("South African Rand", 2), SAR("Saudi Riyal", 2), AED("United Arab Emirates Dirham", 2),
    THB("Thai Baht", 2), MYR("Malaysian Ringgit", 2), IDR("Indonesian Rupiah", 2), PHP("Philippine Peso", 2),
    DKK("Danish Krone", 2), PLN("Polish Złoty", 2), HUF("Hungarian Forint", 2), CZK("Czech Koruna", 2),
    ILS("Israeli New Shekel", 2), ARS("Argentine Peso", 2), CLP("Chilean Peso", 0), COP("Colombian Peso", 2),
    EGP("Egyptian Pound", 2), NGN("Nigerian Naira", 2), PKR("Pakistani Rupee", 2), QAR("Qatari Riyal", 2),
    VND("Vietnamese Đồng", 0), BHD("Bahraini Dinar", 3), JOD("Jordanian Dinar", 3),
    KWD("Kuwaiti Dinar", 3), OMR("Omani Rial", 3), TND("Tunisian Dinar", 3);

    private final String description;
    private final int minorUnitScale;

    Currency(String description, int minorUnitScale) {
        this.description = description;
        this.minorUnitScale = minorUnitScale;
    }

    public String getDescription() {
        return description;
    }

    public int minorUnitScale() {
        return minorUnitScale;
    }

    public static Currency requireSupportedCode(String code) {
        if (code == null || code.isBlank() || !code.equals(code.toUpperCase(java.util.Locale.ROOT))) {
            throw new IllegalArgumentException("currency must be an uppercase ISO 4217 code");
        }
        try {
            return valueOf(code);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("unsupported currency", exception);
        }
    }

    @Override
    public String toString() {
        return name() + " - " + description;
    }
}
