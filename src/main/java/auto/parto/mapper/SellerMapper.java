package auto.parto.mapper;

import auto.parto.dto.SellerDto;
import auto.parto.dto.WalletDto;
import auto.parto.model.Seller;
import auto.parto.model.Wallet;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class SellerMapper {
    public static SellerDto toDto(Seller seller) {
        return SellerDto.builder()
                .id(seller.getId())
                .email(seller.getEmail())
                .phoneNumber(seller.getPhoneNumber())
                .name(seller.getName())
                .description(seller.getDescription())
                .createAt(seller.getCreateAt())
                .modifiedAt(seller.getModifiedAt())
                .walletDto(WalletDto.builder()
                        .id(seller.getWallet().getId())
                        .balance(seller.getWallet().getBalance())
                        .currency(seller.getWallet().getCurrency())
                        .build())
                .addressDto(AddressMapper.toDto(seller.getAddress()))
                .build();
    }

    public static Seller toEntity(SellerDto sellerDto) {
        return Seller.builder()
                .id(UUID.randomUUID().toString())
                .email(sellerDto.getEmail())
                .carPartType(sellerDto.getCarPartType())
                .phoneNumber(sellerDto.getPhoneNumber())
                .address(AddressMapper.toEntity(sellerDto.getAddressDto()))
                .name(sellerDto.getName())
                .description(sellerDto.getDescription())
                .createAt(LocalDateTime.now())
                .modifiedAt(null)
                .wallet(new Wallet(UUID.randomUUID().toString() , new BigDecimal(0) , "MAD"))
                .build();
    }
}
