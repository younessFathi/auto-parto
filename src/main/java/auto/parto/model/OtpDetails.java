package auto.parto.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.UUID;

@Document(collection = "otpDetails")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OtpDetails {
    private UUID id;
    private String otp;
    private LocalDateTime timestamp;
    public boolean isExpired(long otpExpirationMinutes) {
        return timestamp.plusMinutes(otpExpirationMinutes).isBefore(LocalDateTime.now());
    }
}
