package auto.parto.otp;

import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;

@FunctionalInterface
public interface EmailProvider {
    void send(CreateEmailOptions options) throws ResendException;
}
