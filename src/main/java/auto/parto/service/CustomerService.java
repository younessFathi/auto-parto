package auto.parto.service;

import auto.parto.dto.CustomerDto;
import auto.parto.dto.OtpRequestDto;
import auto.parto.mapper.CustomerMapper;
import auto.parto.repository.mongo_repository.CustomerRepository;
import auto.parto.service.iservice.ICustomerService;
import auto.parto.utils.JwtTokenGenerator;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@AllArgsConstructor
@Slf4j
public class CustomerService implements ICustomerService {
    private final CustomerRepository customerRepository;

    @Override
    public Mono<Boolean> checkIfCustomerExistUsingPhoneNumber(String phoneNumber) {
        return customerRepository.exists(phoneNumber);
    }

    @Override
    public Mono<CustomerDto> create(OtpRequestDto otpRequestDto, String otp) {
        return customerRepository.save(
                CustomerMapper.toEntity(otpRequestDto , otp)
        ).map(CustomerMapper::toDto);
    }

    @Override
    public Mono<CustomerDto> findCustomerByPhoneNumber(String phoneNumber) {
        return customerRepository.findByPhoneNumber(phoneNumber).map(CustomerMapper::toDto);
    }

    @Override
    public Mono<CustomerDto> updateGeneratedOtp(String phoneNumber, String sentOtp) {
        return customerRepository.updateGeneratedOtp(phoneNumber, sentOtp , JwtTokenGenerator.keyGenerator(phoneNumber)).map(CustomerMapper::toDto);
    }
}
