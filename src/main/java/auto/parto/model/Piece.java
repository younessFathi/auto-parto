package auto.parto.model;

import auto.parto.enums.Condition;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.UUID;

@Document(collection = "pieces")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Piece {
    private UUID id;
    private String name;           // Name of the car part (e.g., "Brake Pad")
    private String description;      // Description of the part
    private Condition condition;
    private LocalDateTime createAt;
    private LocalDateTime modifiedAt;
}
