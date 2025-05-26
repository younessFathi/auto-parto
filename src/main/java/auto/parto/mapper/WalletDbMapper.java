package auto.parto.mapper;

import auto.parto.dto.WalletDto;
import auto.parto.model.Wallet;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface WalletDbMapper {
    WalletDto toDto(Wallet wallet);
    Wallet toEntity(WalletDto walletDto);
}
