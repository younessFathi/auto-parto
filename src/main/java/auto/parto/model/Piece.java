package auto.parto.model;

import auto.parto.enums.Category;
import auto.parto.enums.Condition;
import auto.parto.enums.SellerType;
import auto.parto.enums.Status;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "pieces")
@CompoundIndexes({
        @CompoundIndex(name = "piece_customer_created_idx", def = "{'customerId': 1, 'createAt': -1}"),
        @CompoundIndex(name = "piece_customer_status_idx", def = "{'customerId': 1, 'status': 1}")
})
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Piece {
    @Id
    private String id;
    private String customerId;      // Buyer who search the car part
    private String name;           // Name of the car part (e.g., "Brake Pad")
    private String description;      // Description of the part
    private Condition condition;
    private LocalDateTime createAt;
    private LocalDateTime modifiedAt;
    private Category category;
    private List<String> imagesId;
    private Integer seeNumber;
    private SellerType sellerType;
    private Double sellerRank;
    private Status status;
}
