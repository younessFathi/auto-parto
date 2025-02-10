package auto.parto.otp;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
@Slf4j
public class EmailService {
  //  private JavaMailSender javaMailSender;

    public Mono<String> sendOTPEmail(String toEmail, String otp) {
        return Mono.just("Email sent successfully");
    }

}
