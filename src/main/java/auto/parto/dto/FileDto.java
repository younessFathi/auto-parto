package auto.parto.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class FileDto {
    private UUID id;
    private String originalFilename;
    private String generatedFilename; // generatedFilename = pieceId + original extension
    private LocalDateTime createdAt;
}
