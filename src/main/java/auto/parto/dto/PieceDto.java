package auto.parto.dto;

import auto.parto.enums.Condition;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PieceDto {
    private UUID id;
    private String name;             // Name of the car part (e.g., "Brake Pad")
    private String description;      // Description of the part
    private Condition condition;
}
// TODO: hetzner vps coolfy deployement
// TODO: mongodb for dev env
