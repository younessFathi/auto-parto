package auto.parto.mapper;

import auto.parto.dto.OtpRequestDto;
import auto.parto.dto.SellerDto;
import auto.parto.model.Buyer;
import auto.parto.model.OtpDetails;
import auto.parto.model.Seller;
import auto.parto.utils.JwtTokenGenerator;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.LocalDateTime;
import java.util.UUID;

@Mapper(
        componentModel = "spring" ,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR ,
        uses = {AddressDbMapper.class, WalletDbMapper.class}
)
public interface SellerDbMapper {
    @Mapping(target = "walletDto", source = "wallet")
    @Mapping(target = "addressDto", source = "address")
    SellerDto toDto(Seller seller);
    @Mapping(target = "wallet", source = "walletDto")
    @Mapping(target = "address", source = "addressDto")
    Seller toEntity(SellerDto sellerDto);

    default Seller toEntity(OtpRequestDto otpRequestDto , String otp) {
        return Seller.builder()
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
