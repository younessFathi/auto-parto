package auto.parto.otp;

import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import auto.parto.config.BoundedProviderScheduler;
import auto.parto.config.ProviderCallTelemetry;
import auto.parto.exception.EmailSendException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Service
public class EmailService {

    private final EmailProvider emailProvider;
    private final Scheduler scheduler;
    private final ProviderCallTelemetry telemetry;
    private final String from;
    private final Resource otpTemplateResource;
    private final String subject;
    private final Duration timeout;
    private final boolean enabled;

    public EmailService(EmailProvider emailProvider,
                        @Qualifier("resendProviderScheduler") BoundedProviderScheduler scheduler,
                        ProviderCallTelemetry telemetry,
                        @Value("${otp.from}") String from,
                        @Value("${otp.template}") Resource otpTemplateResource,
                        @Value("${otp.subject}") String subject,
                        @Value("${auto.parto.providers.email.timeout:10s}") Duration timeout,
                        @Value("${resend.enable:false}") boolean enabled) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("email provider timeout must be positive");
        }
        this.emailProvider = emailProvider;
        this.scheduler = scheduler.scheduler();
        this.telemetry = telemetry;
        this.from = from;
        this.otpTemplateResource = otpTemplateResource;
        this.subject = subject;
        this.timeout = timeout;
        this.enabled = enabled;
    }

    public Mono<String> sendOTPEmail(String to, String otp) {
        if (!enabled) return Mono.just(otp);

        Mono<String> providerCall = Mono.fromCallable(() -> sendBlocking(to, otp))
                .subscribeOn(scheduler);
        return telemetry.observe("resend", "send_email", providerCall, timeout)
                .onErrorMap(error -> error instanceof EmailSendException
                        ? error : new EmailSendException("Email delivery failed"));
    }

    private String sendBlocking(String to, String otp) {
        try {
            String template = StreamUtils.copyToString(otpTemplateResource.getInputStream(), StandardCharsets.UTF_8);
            String htmlContent = template.replace("{{otp_code}}", otp);
            CreateEmailOptions request = CreateEmailOptions.builder()
                    .from(from)
                    .to(to)
                    .subject(subject)
                    .html(htmlContent)
                    .build();
            emailProvider.send(request);
            return otp;
        } catch (ResendException | IOException providerFailure) {
            throw new EmailSendException("Email delivery failed");
        }
    }
}
