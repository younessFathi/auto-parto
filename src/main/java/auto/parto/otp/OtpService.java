package auto.parto.otp;

import auto.parto.dto.CustomerDto;
import auto.parto.dto.OtpDetailsDto;
import auto.parto.exception.OtpExpiredException;
import auto.parto.exception.OtpInvalidException;
import auto.parto.utils.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import java.util.Random;

@Component
@Slf4j
public class OtpService {
    private final Random rand = new Random();
    @Value("${opt.expiration.minutes}")
    private long otpExpirationMinutes;

    public Mono<String> generate() {
        return Mono.just(
                String.format(Constants.OTPConstants.FORMAT, rand.nextInt(10000))
        );
    }

    public Mono<CustomerDto> validate(CustomerDto customerDto, String otp) {
        try {
            log.info("{} : {}" , Constants.OTPConstants.VALIDATE_OTP_CODE_FOR_PHONE_NUMBER, customerDto.getPhoneNumber());
            OtpDetailsDto cachedOtp = customerDto.getOtpDetailsDto();
            checkOtp(customerDto.getPhoneNumber(), otp, cachedOtp);
        } catch (Exception e) {
            return Mono.error(e);
        }
        log.info("{} : {}" , Constants.OTPConstants.END_OF_OTP_VALIDATION_FOR, customerDto.getPhoneNumber());
        return Mono.just(customerDto);
    }

    private void checkOtp(String phoneNumber, String otp, OtpDetailsDto cachedOtp) {
        if (cachedOtp == null) {
            log.info("{} : {}" , Constants.Exception.INVALID_OTP_FOR_PHONE_NUMBER, phoneNumber);
            throw new OtpInvalidException(Constants.Exception.INVALID_OTP);
        }
        if(cachedOtp.isExpired(otpExpirationMinutes)) {
            log.info("{} : {}" , Constants.Exception.EXPIRED_OTP_FOR_PHONE_NUMBER, phoneNumber);
            throw new OtpExpiredException(Constants.Exception.EXPIRED_OTP);
        }

        if (!cachedOtp.getOtp().equals(otp)) {
            log.info("{} : {}" , Constants.Exception.INVALID_OTP_FOR_PHONE_NUMBER, phoneNumber);
            throw new OtpInvalidException(Constants.Exception.INVALID_OTP);
        }
    }
}
