package auto.parto.model;

import auto.parto.enums.*;
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

@Document(collection = "pieceOffers")
@CompoundIndexes({
        @CompoundIndex(name = "offer_piece_created_idx", def = "{'pieceId': 1, 'createAt': -1}"),
        @CompoundIndex(name = "offer_customer_status_idx", def = "{'customerId': 1, 'status': 1}")
})
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PieceOffer {
    @Id
    private String id;
    private String pieceId; // id of piece request
    private String customerId;
    private String name;
    private String description;
    private long priceMinor;
    private Currency currency;
    private LocalDateTime createAt;
    private LocalDateTime modifiedAt;
    private Boolean isNegotiable;
    private List<String> imagesId;
    private Boolean isAvailable;
    private Condition condition;
    private Status status;
    private Integer seeNumber;
    private DeliveryMode deliveryMode;
    private Boolean isFreeDelivery;
    private long deliveryPriceMinor;
    private Integer warranty;
    private Duration duration;
}
