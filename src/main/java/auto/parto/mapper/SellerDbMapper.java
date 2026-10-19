package auto.parto.mapper;

import auto.parto.dto.OtpRequestDto;
import auto.parto.dto.SellerDto;
import auto.parto.model.OtpDetails;
import auto.parto.model.Seller;
import auto.parto.otp.OtpCodeHasher;
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
    @Mapping(source = "otpDetails", target = "otpDetailsDto")
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
                .keyVersion("1")
                .isActive(Boolean.FALSE)
                .otpDetails(
                        OtpDetails.builder()
                                .otpHash(OtpCodeHasher.hashCode(otp))
                                .purpose("LOGIN")
                                .identity(otpRequestDto.getEmail())
                                .timestamp(LocalDateTime.now())
                                .expiresAt(LocalDateTime.now().plusMinutes(5))
                                .build()
                )
                .build();
    }
}
