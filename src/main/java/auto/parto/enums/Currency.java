package auto.parto.enums;

public enum Currency {
    USD("United States Dollar"),
    MAD("MAD"),
    EUR("Euro"),
    GBP("British Pound Sterling"),
    JPY("Japanese Yen"),
    CNY("Chinese Yuan"),
    INR("Indian Rupee"),
    AUD("Australian Dollar"),
    CAD("Canadian Dollar"),
    CHF("Swiss Franc"),
    SEK("Swedish Krona"),
    NZD("New Zealand Dollar"),
    MXN("Mexican Peso"),
    SGD("Singapore Dollar"),
    HKD("Hong Kong Dollar"),
    NOK("Norwegian Krone"),
    KRW("South Korean Won"),
    TRY("Turkish Lira"),
    RUB("Russian Ruble"),
    BRL("Brazilian Real"),
    ZAR("South African Rand"),
    SAR("Saudi Riyal"),
    AED("United Arab Emirates Dirham"),
    THB("Thai Baht"),
    MYR("Malaysian Ringgit"),
    IDR("Indonesian Rupiah"),
    PHP("Philippine Peso"),
    DKK("Danish Krone"),
    PLN("Polish Złoty"),
    HUF("Hungarian Forint"),
    CZK("Czech Koruna"),
    ILS("Israeli New Shekel"),
    ARS("Argentine Peso"),
    CLP("Chilean Peso"),
    COP("Colombian Peso"),
    EGP("Egyptian Pound"),
    NGN("Nigerian Naira"),
    PKR("Pakistani Rupee"),
    QAR("Qatari Riyal"),
    VND("Vietnamese Đồng");

    private final String description;

    Currency(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return name() + " - " + description;
    }
}
