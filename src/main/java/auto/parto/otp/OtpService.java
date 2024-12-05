package auto.parto.otp;

import auto.parto.dto.CustomerDto;
import auto.parto.dto.OtpDetailsDto;
import auto.parto.dto.OtpRequestDto;
import auto.parto.exception.OtpExpiredException;
import auto.parto.exception.OtpInvalidException;
import auto.parto.service.CustomerService;
import auto.parto.utils.Constants;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import java.util.Random;

@Component
@Slf4j
public class OtpService {
    public static final String END_OF_OTP_VALIDATION_FOR = "End of otp validation for";
    @Getter
    private final Random rand = new Random();
    private final long otpExpirationMinutes;
    private final CustomerService customerService;

    public OtpService(CustomerService customerService , @Value("${opt.expiration.minutes}") long otpExpirationMinutes) {
        this.customerService = customerService;
        this.otpExpirationMinutes = otpExpirationMinutes;
    }

    public Mono<String> generate(OtpRequestDto otpRequestDto) {
        return Mono.just(
                String.format(Constants.OTPConstants.FORMAT, rand.nextInt(10000))
        );
    }

    public Mono<String> validate(CustomerDto customerDto, String otp) {
        try {
            log.info("{} : {}" , Constants.OTPConstants.VALIDATE_OTP_CODE_FOR_PHONE_NUMBER, customerDto.getPhoneNumber());
            OtpDetailsDto cachedOtp = customerDto.getOtpDetailsDto();
            checkOtp(customerDto.getPhoneNumber(), otp, cachedOtp);
        } catch (Exception e) {
            return Mono.error(e);
        }
        log.info("{} : {}" , END_OF_OTP_VALIDATION_FOR, customerDto.getPhoneNumber());
        return Mono.just(customerDto.getJwtKey());
    }

    private void checkOtp(String phoneNumber, String otp, OtpDetailsDto cachedOtp) {
        if (cachedOtp == null) {
            log.info("{} : {}" ,Constants.ExceptionConstants.INVALID_OTP_FOR_PHONE_NUMBER, phoneNumber);
            throw new OtpInvalidException(Constants.ExceptionConstants.INVALID_OTP);
        }

        if(cachedOtp.isExpired(otpExpirationMinutes)) {
            log.info("{} : {}" , Constants.ExceptionConstants.EXPIRED_OTP_FOR_PHONE_NUMBER, phoneNumber);
            throw new OtpExpiredException(Constants.ExceptionConstants.EXPIRED_OTP);
        }

        if (!cachedOtp.getOtp().equals(otp)) {
            log.info("{} : {}" , Constants.ExceptionConstants.INVALID_OTP_FOR_PHONE_NUMBER, phoneNumber);
            throw new OtpInvalidException(Constants.ExceptionConstants.INVALID_OTP);
        }
    }
}
