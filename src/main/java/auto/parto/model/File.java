package auto.parto.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.UUID;

@Document(collection = "files")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class File {
    private UUID id;
    private String originalFilename;
    private String generatedFilename; // generatedFilename = pieceId + original extension
    private LocalDateTime createdAt;
}
