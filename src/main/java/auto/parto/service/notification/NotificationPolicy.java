package auto.parto.service.notification;

import java.util.Locale;

/** Explicit compatibility mapping shared by queries and every delivery transport. */
public final class NotificationPolicy {
    private NotificationPolicy() { }

    public static String channel(String value) {
        if (value == null) throw new IllegalArgumentException("notification channel is required");
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "push", "sse", "fcm" -> "push";
            case "email" -> "email";
            case "sms" -> "sms";
            default -> throw new IllegalArgumentException("unsupported notification channel");
        };
    }

    public static String category(String value) {
        if (value == null) throw new IllegalArgumentException("notification type is required");
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "newoffer", "offer_created", "seller_response", "seller.response" -> "newoffer";
            case "pricealert" -> "pricealert";
            case "searchmatch", "new_search_match", "new_search_posted", "search.new",
                 "multiple_matches", "search.matches.multiple", "inventory_match", "inventory.match",
                 "urgent_search", "search.urgent" -> "searchmatch";
            case "offer_accepted", "offer.accepted", "orderupdate", "search_expired", "search.expired", "search_fulfilled", "search.fulfilled" -> "orderupdate";
            case "marketing" -> "marketing";
            default -> throw new IllegalArgumentException("unsupported notification type");
        };
    }
}
