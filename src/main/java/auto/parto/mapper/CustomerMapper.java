package auto.parto.mapper;

import auto.parto.dto.CustomerDto;
import auto.parto.dto.OtpDetailsDto;
import auto.parto.dto.OtpRequestDto;
import auto.parto.model.Customer;
import auto.parto.model.OtpDetails;
import auto.parto.utils.JwtTokenGenerator;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class CustomerMapper {
    public static CustomerDto toDto(Customer customer) {
        return CustomerDto.builder()
                .id(customer.getId())
                .email(customer.getEmail())
                .firstName(customer.getFirstName())
                .lastName(customer.getLastName())
                .phoneNumber(customer.getPhoneNumber())
                .jwtKey(customer.getJwtKey())
                .isActive(customer.getIsActive())
                .otpDetailsDto(
                        OtpDetailsDto.builder()
                                .otp(customer.getOtpDetails().getOtp())
                                .timestamp(customer.getOtpDetails().getTimestamp())
                                .build()
                )
                .build();
    }

    public static Customer toEntity(CustomerDto customerDto) {
        return Customer.builder()
                .firstName(customerDto.getFirstName())
                .lastName(customerDto.getLastName())
                .phoneNumber(customerDto.getPhoneNumber())
                .email(customerDto.getEmail())
                .isActive(Boolean.TRUE)
                .otpDetails(
                        OtpDetails.builder()
                                .otp(customerDto.getOtpDetailsDto().getOtp())
                                .timestamp(customerDto.getOtpDetailsDto().getTimestamp())
                                .build()
                )
                .build();
    }

    public static Customer toEntity(OtpRequestDto otpRequestDto , String otp) {
        return Customer.builder()
                .id(UUID.randomUUID().toString())
                .firstName(otpRequestDto.getFirstName())
                .lastName(otpRequestDto.getLastName())
                .phoneNumber(otpRequestDto.getPhoneNumber())
                .email(otpRequestDto.getEmail())
                .jwtKey(JwtTokenGenerator.keyGenerator(otpRequestDto.getPhoneNumber()))
                .isActive(Boolean.TRUE)
                .otpDetails(
                        OtpDetails.builder()
                                .otp(otp)
                                .timestamp(LocalDateTime.now())
                                .build()
                )
                .build();
    }


}
