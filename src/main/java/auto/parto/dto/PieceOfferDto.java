package auto.parto.dto;

import auto.parto.enums.*;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PieceOfferDto {
    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private String id;
    @NotBlank(message = "Piece ID is required")
    private String pieceId; // id of piece request
    @NotBlank(message = "Customer ID is required")
    private String customerId;
    @NotBlank(message = "Name is required")
    private String name;
    @NotBlank(message = "Description is required")
    private String description;
    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Price must be greater than zero")
    private BigDecimal price;
    @NotNull(message = "Currency is required")
    private Currency currency;
    @NotNull(message = "Condition is required")
    private Condition condition;
    private LocalDateTime createAt;
    private LocalDateTime modifiedAt;
    @NotNull(message = "Negotiable status is required")
    private Boolean isNegotiable;
    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private List<String> imagesId;
    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private Boolean isAvailable;
    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private Status status;
    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private Integer seeNumber;
    @NotNull(message = "Delivery mode is required")
    private DeliveryMode deliveryMode;
    @NotNull(message = "Free delivery status is required")
    private Boolean isFreeDelivery;
    private BigDecimal deliveryPrice;
    private Integer warranty;
    private Duration duration;
}
