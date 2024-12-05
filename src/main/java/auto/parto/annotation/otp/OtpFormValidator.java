package auto.parto.annotation.otp;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class OtpFormValidator implements ConstraintValidator<ValidOtp , String> {
    String otpRegex = "\\d{4}";
    @Override
    public boolean isValid(String s, ConstraintValidatorContext constraintValidatorContext) {
        if(s == null || s.isEmpty()) return false;
        return s.matches(otpRegex);
    }
}
