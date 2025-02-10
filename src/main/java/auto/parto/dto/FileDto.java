package auto.parto.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private String id;
    private String originalFilename;
    private String generatedFilename;
    private LocalDateTime createdAt;
    private String folderName;
}
