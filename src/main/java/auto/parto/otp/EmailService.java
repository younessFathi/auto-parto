package auto.parto.otp;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Service
@Slf4j
public class EmailService {

    @Value("${resend.api-key}")
    private String resendApiKey;
    @Value("${otp.from}")
    private String from;
    @Value("${otp.template}")
    private Resource otpTemplateResource;
    @Value("${otp.subject}")
    private String subject;
    @Value("${resend.enable}")
    private boolean isEnalbled;

    public Mono<String> sendOTPEmail(String to, String otp) {
        if (isEnalbled){
            Resend resend = new Resend(resendApiKey);
            try {
                // 1. Read the template file into a String
                String template = StreamUtils.copyToString(
                        otpTemplateResource.getInputStream(),
                        StandardCharsets.UTF_8
                );
                // 2. Execute your specific replacement code
                String htmlContent = template.replace("{{otp_code}}", otp);
                CreateEmailOptions sendEmailRequest = CreateEmailOptions.builder()
                        .from(from)
                        .to(to)
                        .subject(subject)
                        .html(htmlContent)
                        .build();

                CreateEmailResponse data = resend.emails().send(sendEmailRequest);
            } catch (ResendException e) {
                return Mono.error(new RuntimeException("Error sending email: " + e.getMessage()));
            } catch (IOException e) {
                return Mono.error(new RuntimeException("Error reading template file: " + e.getMessage()));
            }
        }
        return Mono.just(otp);
    }
}
