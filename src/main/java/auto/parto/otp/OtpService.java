package auto.parto.otp;

import auto.parto.dto.OtpDetailsDto;
import auto.parto.dto.OtpVerifiable;
import auto.parto.exception.OtpExpiredException;
import auto.parto.exception.OtpInvalidException;
import auto.parto.utils.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import java.security.SecureRandom;

@Component
@Slf4j
public class OtpService {
    private final SecureRandom secureRandom = new SecureRandom();
    @Value("${opt.expiration.minutes}")
    private long otpExpirationMinutes;

    public String generate() {
        int otp = secureRandom.nextInt(1_000_000);
        return String.format("%06d", otp);
    }

    public <T extends OtpVerifiable> Mono<T> validate(T user, String otp) {
        try {
            log.info("{} : {}" , Constants.OTPConstants.VALIDATE_OTP_CODE_FOR, user.getId());
            OtpDetailsDto cachedOtp = user.getOtpDetailsDto();
            checkOtp(user.getId(), otp, cachedOtp);
        } catch (Exception e) {
            return Mono.error(e);
        }
        log.info("{} : {}" , Constants.OTPConstants.END_OF_OTP_VALIDATION_FOR, user.getId());
        return Mono.just(user);
    }

    private void checkOtp(String id, String otp, OtpDetailsDto cachedOtp) {
        if (cachedOtp == null) {
            log.info("{} : {}" , Constants.Exception.INVALID_OTP_FOR, id);
            throw new OtpInvalidException(Constants.Exception.INVALID_OTP);
        }
        if(cachedOtp.isExpired(otpExpirationMinutes)) {
            log.info("{} : {}" , Constants.Exception.EXPIRED_OTP_FOR, id);
            throw new OtpExpiredException(Constants.Exception.EXPIRED_OTP);
        }

        if (!cachedOtp.getOtp().equals(otp)) {
            log.info("{} : {}" , Constants.Exception.INVALID_OTP_FOR, id);
            throw new OtpInvalidException(Constants.Exception.INVALID_OTP);
        }
    }
}
