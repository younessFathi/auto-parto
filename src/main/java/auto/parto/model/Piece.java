package auto.parto.model;

import auto.parto.enums.Category;
import auto.parto.enums.Condition;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Document(collection = "pieces")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Piece {
    private String id;
    private String name;           // Name of the car part (e.g., "Brake Pad")
    private String description;      // Description of the part
    private Condition condition;
    private LocalDateTime createAt;
    private LocalDateTime modifiedAt;
    private Category category;
    private List<File> images;
}
