package auto.parto.otp;

import auto.parto.dto.OtpRequestDto;

public interface OtpSender {
    void send(OtpRequestDto otpRequestDto);
}
