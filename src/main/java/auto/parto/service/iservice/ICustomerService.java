package auto.parto.service.iservice;

import auto.parto.annotation.phoneNumber.ValidPhoneNumber;
import auto.parto.dto.CustomerDto;
import auto.parto.dto.OtpRequestDto;
import jakarta.validation.constraints.Email;
import reactor.core.publisher.Mono;

public interface ICustomerService {
    Mono<Boolean> checkIfCustomerExistUsingPhoneNumber(String phoneNumber);
    Mono<Boolean> checkIfCustomerExistUsingEmail(String email);
    Mono<CustomerDto> create(OtpRequestDto otpRequestDto , String otp);
    Mono<CustomerDto> findCustomerByPhoneNumber( @ValidPhoneNumber String phoneNumber);
    Mono<CustomerDto> updateGeneratedOtpByPhoneNumber(String phoneNumber, String sentOtp);
    Mono<CustomerDto> updateGeneratedOtpByEmail(String email, String sentOtp);
    Mono<CustomerDto> findCustomerByEmail( @Email String email);
    Mono<Boolean> checkIfCustomerExistUsingId(String id);
    Mono<CustomerDto> deactivateCustomer(String id);
}
