package auto.parto.otp;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class ResendEmailProvider implements EmailProvider {

    private final Resend resend;

    public ResendEmailProvider(@Value("${resend.api-key:}") String apiKey) {
        this.resend = new Resend(apiKey);
    }

    @Override
    public void send(CreateEmailOptions options) throws ResendException {
        resend.emails().send(options);
    }
}
