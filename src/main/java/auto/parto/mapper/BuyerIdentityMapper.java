package auto.parto.mapper;

import auto.parto.dto.BuyerDto;
import auto.parto.persistence.BuyerIdentityRecord;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.UUID;

@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface BuyerIdentityMapper {

    @Mapping(source = "publicId", target = "id")
    @Mapping(source = "active", target = "isActive")
    BuyerDto toDto(BuyerIdentityRecord identity);

    default String map(UUID publicId) {
        return publicId == null ? null : publicId.toString();
    }
}
