package auto.parto.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.UUID;

@Document(collection = "customers_jwt")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CustomerJwt {
    private UUID id;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String key;
}
