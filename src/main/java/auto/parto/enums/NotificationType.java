package auto.parto.enums;

import lombok.Getter;

@Getter
public enum NotificationType {
    // For sellers - When a new search is posted
    NEW_SEARCH_POSTED("search.new", "New search posted", Priority.HIGH,
            "🚨 New search for {partName} in {location}"),

    // For buyers - When sellers respond
    SELLER_RESPONSE("seller.response", "Seller responded to your search", Priority.MEDIUM,
            "👋 {sellerName} has the part you're looking for"),

    // For buyers - Multiple matches found
    MULTIPLE_MATCHES("search.matches.multiple", "Multiple sellers found", Priority.MEDIUM,
            "🎯 {count} sellers have {partName}"),

    // For sellers - When search matches their inventory
    INVENTORY_MATCH("inventory.match", "Search matches your inventory", Priority.HIGH,
            "✅ Search matches your {partName} stock"),

    // Urgent searches
    URGENT_SEARCH("search.urgent", "Urgent search posted", Priority.URGENT,
            "🔥 URGENT: {user} needs {partName} immediately"),

    // Search expired
    SEARCH_EXPIRED("search.expired", "Search has expired", Priority.LOW,
            "⏰ Your search for {partName} has expired"),

    // Search fulfilled
    SEARCH_FULFILLED("search.fulfilled", "Search was fulfilled", Priority.LOW,
            "✅ Your search for {partName} was fulfilled by {sellerName}");

    private final String code;
    private final String description;
    private final Priority priority;
    private final String template;

    NotificationType(String code, String description, Priority priority, String template) {
        this.code = code;
        this.description = description;
        this.priority = priority;
        this.template = template;
    }

    // Constructor and getters

    public boolean isForSellers() {
        return this == NEW_SEARCH_POSTED ||
                this == INVENTORY_MATCH ||
                this == URGENT_SEARCH;
    }

    public boolean isForBuyers() {
        return this == SELLER_RESPONSE ||
                this == MULTIPLE_MATCHES ||
                this == SEARCH_EXPIRED ||
                this == SEARCH_FULFILLED;
    }
}
