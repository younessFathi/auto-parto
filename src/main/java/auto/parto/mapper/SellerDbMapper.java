package auto.parto.mapper;

import auto.parto.dto.SellerDto;
import auto.parto.model.Seller;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

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
}
