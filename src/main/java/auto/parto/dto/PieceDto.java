package auto.parto.dto;

import auto.parto.enums.Category;
import auto.parto.enums.Condition;
import auto.parto.enums.SellerType;
import auto.parto.enums.Status;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PieceDto {
    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private String id;
    @NotBlank(message = "Customer ID is mandatory")
    private String customerId;
    @NotBlank(message = "Name is mandatory")
    @Size(max = 100, message = "Name must be less than 100 characters")
    private String name;
    @Size(max = 500, message = "Description must be less than 500 characters")
    private String description;
    @NotNull(message = "Condition is mandatory")
    private Condition condition;
    @NotNull(message = "Category is mandatory")
    private Category category;
    private LocalDateTime createAt;
    private LocalDateTime modifiedAt;
    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private List<String> imagesId;
    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private Integer seeNumber;
    @NotNull(message = "Seller type is mandatory")
    private SellerType sellerType;
    @NotNull(message = "Seller Rank is mandatory")
    private Double sellerRank;
    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private Status status;
}
// TODO: hetzner vps coolfy deployement
// TODO: mongodb for dev env
