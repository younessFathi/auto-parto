package auto.parto.service.iservice;

import auto.parto.dto.CustomerDto;
import auto.parto.dto.OtpRequestDto;
import reactor.core.publisher.Mono;

public interface ICustomerService {
    Mono<Boolean> checkIfCustomerExistUsingPhoneNumber(String customerId);
    Mono<CustomerDto> create(OtpRequestDto otpRequestDto , String otp);
    Mono<CustomerDto> findCustomerByPhoneNumber(String phoneNumber);
    Mono<CustomerDto> updateGeneratedOtp(String phoneNumber, String sentOtp);
}
