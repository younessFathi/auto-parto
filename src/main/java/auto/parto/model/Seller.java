package auto.parto.model;

import auto.parto.enums.CarPartType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "sellers")
@CompoundIndexes({
        @CompoundIndex(name = "seller_email_active_idx", def = "{'email': 1, 'isActive': 1}")
})
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Seller {
    @Id
    private String id;
    private String firstName;
    private String lastName;
    @Indexed
    private String email;
    private String phoneNumber;
    private Address address;
    private CarPartType carPartType;
    private String description;
    private LocalDateTime createAt;
    private LocalDateTime modifiedAt;
    private Wallet wallet;
    private Boolean isActive;
    private String keyVersion;
    private OtpDetails otpDetails;
}
