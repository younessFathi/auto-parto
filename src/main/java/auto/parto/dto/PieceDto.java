package auto.parto.dto;

import auto.parto.enums.Category;
import auto.parto.enums.Condition;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PieceDto {
    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private String id;
    private String name;             // Name of the car part (e.g., "Brake Pad")
    private String description;      // Description of the part
    private Condition condition;
    private Category category;
    private LocalDateTime createAt;
    private LocalDateTime modifiedAt;
    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private List<FileDto> images;
}
// TODO: hetzner vps coolfy deployement
// TODO: mongodb for dev env
