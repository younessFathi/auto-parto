package auto.parto.annotation.phoneNumber;

import auto.parto.utils.Constants;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = PhoneNumberValidator.class)
@Target({ElementType.PARAMETER , ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPhoneNumber {
    String message() default Constants.ExceptionConstants.INVALID_PHONE_NUMBER;
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
