package auto.parto.model;

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

@Document(collection = "buyers")
@CompoundIndexes({
        @CompoundIndex(name = "buyer_email_active_idx", def = "{'email': 1, 'isActive': 1}")
})
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Buyer {
    @Id
    private String id;
    @Indexed
    private String clerkId;
    private String firstName;
    private String lastName;
    @Indexed
    private String email;
    @Indexed
    private String phoneNumber;
    private LocalDateTime createAt;
    private LocalDateTime modifiedAt;
    private OtpDetails otpDetails;
    private String keyVersion;
    private Boolean isActive;
    private String picture;
}
