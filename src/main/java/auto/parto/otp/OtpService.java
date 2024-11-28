package auto.parto.otp;

import auto.parto.dto.OtpDetailsDto;
import auto.parto.dto.OtpRequestDto;
import auto.parto.dto.OtpRequestWithGeneratedOtpDetailsDto;
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
import java.util.Objects;
import java.util.Random;

@Component
@Slf4j
public class OtpService {
    private final Map<String , OtpRequestWithGeneratedOtpDetailsDto> otps = new HashMap<>();
    private final Random rand = new Random();
    @Value("${opt.expiration.minutes}")
    private long otpExpirationMinutes;

    public String generate(OtpRequestDto otpRequestDto) {
        if(isOtpExistAndNotExpired(otpRequestDto)) {
            log.info(Constants.ExceptionConstants.TOO_MANY_OTP_REQUESTS_FOR_PHONE_NUMBER, otpRequestDto);
            throw new OtpTooManyRequestsException(Constants.ExceptionConstants.MANY_OTP_REQUESTS_FOR_PHONE_NUMBER);
        }
        log.info(Constants.OTPConstants.GENERATE_OTP_CODE_FOR_PHONE_NUMBER, otpRequestDto);
        String otp = String.format(Constants.OTPConstants.FORMAT, rand.nextInt(10000));
        OtpDetailsDto otpDetailsDto = new OtpDetailsDto(otp , LocalDateTime.now() , otpExpirationMinutes);
        otps.put(otpRequestDto.getPhoneNumber(), new OtpRequestWithGeneratedOtpDetailsDto(otpRequestDto , otpDetailsDto));
        log.info(Constants.OTPConstants.END_OF_GENERATE_OTP_CODE_FOR_PHONE_NUMBER, otpRequestDto);
        return otp;
    }

    private boolean isOtpExistAndNotExpired(OtpRequestDto otpRequestDto) {
        return otps.containsKey(otpRequestDto.getPhoneNumber()) &&
                !otps.get(otpRequestDto.getPhoneNumber()).getOtpDetailsDto().isExpired();
    }

    public boolean validate(String phoneNumber, String otp) {
        log.info(Constants.OTPConstants.VALIDATE_OTP_CODE_FOR_PHONE_NUMBER, phoneNumber);
        OtpRequestWithGeneratedOtpDetailsDto otpRequestWithGeneratedOtpDetailsDto = otps.get(phoneNumber);
        OtpDetailsDto cachedOtp = Objects.nonNull(otpRequestWithGeneratedOtpDetailsDto) ? otpRequestWithGeneratedOtpDetailsDto.getOtpDetailsDto():null;
        checkOtp(phoneNumber, otp, cachedOtp);
        return Boolean.TRUE;
    }

    private void checkOtp(String phoneNumber, String otp, OtpDetailsDto cachedOtp) {
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
}
