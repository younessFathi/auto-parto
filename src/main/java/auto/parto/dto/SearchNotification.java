package auto.parto.dto;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchNotification {

    private String id;
    private String userId; // Buyer who is searching
    private String buyerLocation;

    // Part details
    private String partName;
    private String partNumber;
    private String oemNumber;
    private String carMake;
    private String carModel;
    private Integer year;
    private String category; // Engine, Brakes, Electrical, etc.

    // Search criteria
    private Double maxPrice;
    private Integer quantity;
    private String urgency; // IMMEDIATE, WITHIN_DAY, FLEXIBLE
    private String searchType; // IMAGE, TEXT, VIN, PART_NUMBER
    private String imageUrl;


    // Metadata
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private LocalDateTime searchTime;

    @Builder.Default
    private Set<String> matchingSellerIds = Set.of();

    // Factory methods
    public static SearchNotification createImageSearch(String userId, String imageUrl, String partName) {
        return SearchNotification.builder()
                .id(generateId())
                .userId(userId)
                .partName(partName)
                .searchType("IMAGE")
                .imageUrl(imageUrl)
                .urgency("WITHIN_DAY")
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();
    }

    public static SearchNotification createTextSearch(String userId, String partName, String carMake, String carModel) {
        return SearchNotification.builder()
                .id(generateId())
                .userId(userId)
                .partName(partName)
                .carMake(carMake)
                .carModel(carModel)
                .searchType("TEXT")
                .urgency("FLEXIBLE")
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(3))
                .build();
    }

    public static SearchNotification createUrgentSearch(String userId, String partName, String location) {
        return SearchNotification.builder()
                .id(generateId())
                .userId(userId)
                .partName(partName)
                .buyerLocation(location)
                .searchType("TEXT")
                .urgency("IMMEDIATE")
                .maxPrice(0.0) // No price limit for urgent searches
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusHours(6))
                .build();
    }

    // Helper methods
    public boolean isUrgent() {
        return "IMMEDIATE".equals(urgency);
    }

    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean hasPriceLimit() {
        return maxPrice != null && maxPrice > 0;
    }

    public String getSearchDescription() {
        StringBuilder desc = new StringBuilder();
        desc.append(partName);

        if (carMake != null) {
            desc.append(" for ").append(carMake);
            if (carModel != null) {
                desc.append(" ").append(carModel);
            }
        }

        if (year != null) {
            desc.append(" (").append(year).append(")");
        }

        return desc.toString();
    }

    private static String generateId() {
        return "search-" + UUID.randomUUID().toString();
    }
}