package auto.parto.mapper;

import auto.parto.dto.BuyerDto;
import auto.parto.dto.OtpRequestDto;
import auto.parto.model.Buyer;
import auto.parto.model.OtpDetails;
import auto.parto.utils.JwtTokenGenerator;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.LocalDateTime;
import java.util.UUID;

@Mapper(componentModel = "spring" , injectionStrategy = InjectionStrategy.CONSTRUCTOR, uses = {OtpDbMapper.class})
public interface BuyerDbMapper {

    @Mapping(source = "otpDetails", target = "otpDetailsDto")
    BuyerDto toDto(Buyer customer);

    @Mapping(source = "otpDetailsDto", target = "otpDetails")
    Buyer toEntity(BuyerDto customerDto);


    default Buyer toEntity(OtpRequestDto otpRequestDto , String otp) {
        return Buyer.builder()
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
