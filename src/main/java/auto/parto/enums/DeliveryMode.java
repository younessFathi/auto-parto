package auto.parto.enums;

public enum DeliveryMode {
    STANDARD("Standard Delivery", "Livraison Standard"),
    EXPRESS("Express Delivery", "Livraison Express"),
    SAME_DAY("Same-day Delivery", "Livraison le Jour Même"),
    OVERNIGHT("Overnight Delivery", "Livraison de Nuit"),
    PICKUP("In-store Pickup", "Retrait en Magasin"),
    INTERNATIONAL("International Delivery", "Livraison Internationale");

    private final String descriptionEn;
    private final String descriptionFr;

    DeliveryMode(String descriptionEn, String descriptionFr) {
        this.descriptionEn = descriptionEn;
        this.descriptionFr = descriptionFr;
    }

    public String getDescriptionEn() {
        return descriptionEn;
    }

    public String getDescriptionFr() {
        return descriptionFr;
    }
}
