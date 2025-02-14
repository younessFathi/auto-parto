package auto.parto.dto;

import auto.parto.enums.Category;
import auto.parto.enums.Condition;
import auto.parto.enums.SellerType;
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
public class PieceWithFilesDto {
    private String id;
    private String customerId;
    private String name;
    private String description;
    private Condition condition;
    private Category category;
    private LocalDateTime createAt;
    private List<FileDto> images;
    private Integer seeNumber;
}
