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
        return customerRepository.exists(phoneNumber, "phoneNumber");
    }

    @Override
    public Mono<Boolean> checkIfCustomerExistUsingEmail(String email) {
        return customerRepository.exists(email, "email");
    }

    @Override
    public Mono<Boolean> checkIfCustomerExistUsingId(String id) {
        log.info("Check if customer exist using id {} " , id);
        return customerRepository.existsById(id);
    }

    @Override
    public Mono<CustomerDto> deactivateCustomer(String id) {
        log.info("Deactivate customer by id : {} " , id);
        return customerRepository.deactivateCustomer(id).map(CustomerMapper::toDto);
    }

    @Override
    public Mono<CustomerDto> createCustomerFromClerkAuth(CustomerDto customerDto) {
        return customerRepository.save(
                CustomerMapper.toEntity(customerDto)
        ).map(CustomerMapper::toDto);
    }

    @Override
    public Mono<Boolean> checkIfCustomerExistClerkId(String userId) {
        return customerRepository.exists(userId, "clerkId");
    }

    @Override
    public Mono<CustomerDto> create(OtpRequestDto otpRequestDto, String otp) {
        log.info("Creating new customer with phone number: {}", otpRequestDto.getPhoneNumber());
        return customerRepository.save(
                CustomerMapper.toEntity(otpRequestDto , otp)
        ).map(CustomerMapper::toDto);
    }

    @Override
    public Mono<CustomerDto> findCustomerByPhoneNumber(String phoneNumber) {
        return customerRepository.findBy(phoneNumber, "phoneNumber").map(CustomerMapper::toDto);
    }

    @Override
    public Mono<CustomerDto> updateGeneratedOtpByPhoneNumber(String phoneNumber, String sentOtp) {
        log.info("Updating generated OTP for phone number: {}", phoneNumber);
        return customerRepository.updateGeneratedOtp(
                            phoneNumber, sentOtp ,
                            JwtTokenGenerator.keyGenerator(phoneNumber),
                        "phoneNumber"
                        ).map(CustomerMapper::toDto);
    }

    @Override
    public Mono<CustomerDto> updateGeneratedOtpByEmail(String email, String sentOtp) {
        log.info("Updating generated OTP for email: {}", email);
        return customerRepository.updateGeneratedOtp(
                email, sentOtp ,
                JwtTokenGenerator.keyGenerator(email),
                "email"
        ).map(CustomerMapper::toDto);
    }

    @Override
    public Mono<CustomerDto> findCustomerByEmail(String email) {
        return customerRepository.findBy(email , "email").map(CustomerMapper::toDto);
    }
}
