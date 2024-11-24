package auto.parto.otp;

import auto.parto.exception.OtpExpiredException;
import auto.parto.exception.OtpInvalidException;
import auto.parto.exception.OtpTooManyRequestsException;
import auto.parto.utils.Constants;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@Component
@Slf4j
public class OtpService {
    private final Map<String , OtpDetails> otps = new HashMap<>();
    private final Random rand = new Random();
    @Value("${opt.expiration.minutes}")
    private long otpExpirationMinutes;

    public String generate(String phoneNumber) {
        if(otps.containsKey(phoneNumber) && !otps.get(phoneNumber).isExpired()) {
            log.info(Constants.ExceptionConstants.TOO_MANY_OTP_REQUESTS_FOR_PHONE_NUMBER, phoneNumber);
            throw new OtpTooManyRequestsException(Constants.ExceptionConstants.MANY_OTP_REQUESTS_FOR_PHONE_NUMBER);
        }
        log.info(Constants.OTPConstants.GENERATE_OTP_CODE_FOR_PHONE_NUMBER, phoneNumber);
        String otp = String.format(Constants.OTPConstants.FORMAT, rand.nextInt(10000));

        otps.put(phoneNumber, new OtpDetails(otp , LocalDateTime.now() , otpExpirationMinutes));
        log.info(Constants.OTPConstants.END_OF_GENERATE_OTP_CODE_FOR_PHONE_NUMBER, phoneNumber);
        return otp;
    }

    public boolean validate(String phoneNumber, String otp) {
        log.info(Constants.OTPConstants.VALIDATE_OTP_CODE_FOR_PHONE_NUMBER, phoneNumber);
        OtpDetails cachedOtp = otps.get(phoneNumber);
        checkOtp(phoneNumber, otp, cachedOtp);
        return Boolean.TRUE;
    }

    private void checkOtp(String phoneNumber, String otp, OtpDetails cachedOtp) {
        if (cachedOtp == null) {
            log.info(Constants.ExceptionConstants.INVALID_OTP_FOR_PHONE_NUMBER, phoneNumber);
            throw new OtpInvalidException(Constants.ExceptionConstants.INVALID_OTP);
        }
        if(cachedOtp.isExpired()) {
            log.info(Constants.ExceptionConstants.EXPIRED_OTP_FOR_PHONE_NUMBER, phoneNumber);
            throw new OtpExpiredException(Constants.ExceptionConstants.EXPIRED_OTP);
        }
        if (!cachedOtp.getOtp().equals(otp)) {
            log.info(Constants.ExceptionConstants.INVALID_OTP_FOR_PHONE_NUMBER, phoneNumber);
            throw new OtpInvalidException(Constants.ExceptionConstants.INVALID_OTP);
        }
    }

    private static class OtpDetails {
        @Getter
        private final String otp;
        private final LocalDateTime timestamp;
        private final long otpExpirationMinutes;

        public OtpDetails(String otp, LocalDateTime timestamp , long otpExpirationMinutes) {
            this.otp = otp;
            this.timestamp = timestamp;
            this.otpExpirationMinutes = otpExpirationMinutes;
        }

        public boolean isExpired() {
            return timestamp.plusMinutes(otpExpirationMinutes).isBefore(LocalDateTime.now());
        }
    }
}
