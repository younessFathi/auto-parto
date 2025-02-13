package auto.parto.model;

import auto.parto.enums.CarPartType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "sellers")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Seller {
    @Id
    private String id;
    private String name;
    private String email;
    private String phoneNumber;
    private Address address;
    private CarPartType carPartType;
    private String description;
    private LocalDateTime createAt;
    private LocalDateTime modifiedAt;
    private Wallet wallet;
    private Boolean isActive;
}
