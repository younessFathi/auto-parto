package auto.parto.annotation.otp;

import auto.parto.utils.Constants;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = OtpFormValidator.class)
@Target({ElementType.PARAMETER , ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidOtp {
    String message() default Constants.Exception.INVALID_OTP;
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
