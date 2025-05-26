package auto.parto.mapper;

import auto.parto.dto.CustomerDto;
import auto.parto.dto.OtpRequestDto;
import auto.parto.model.Customer;
import auto.parto.model.OtpDetails;
import auto.parto.utils.JwtTokenGenerator;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.LocalDateTime;
import java.util.UUID;

@Mapper(componentModel = "spring" , injectionStrategy = InjectionStrategy.CONSTRUCTOR, uses = {OtpDbMapper.class})
public interface CustomerDbMapper {

    @Mapping(source = "otpDetails", target = "otpDetailsDto")
    CustomerDto toDto(Customer customer);

    @Mapping(source = "otpDetailsDto", target = "otpDetails")
    Customer toEntity(CustomerDto customerDto);

    default Customer toEntity(OtpRequestDto otpRequestDto , String otp) {
        return Customer.builder()
                .id(UUID.randomUUID().toString())
                .firstName(otpRequestDto.getFirstName())
                .lastName(otpRequestDto.getLastName())
                .phoneNumber(otpRequestDto.getPhoneNumber())
                .email(otpRequestDto.getEmail())
                .createAt(LocalDateTime.now())
                .modifiedAt(null)
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
