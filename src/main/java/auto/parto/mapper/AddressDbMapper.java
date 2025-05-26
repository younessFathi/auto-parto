package auto.parto.mapper;

import auto.parto.dto.AddressDto;
import auto.parto.model.Address;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AddressDbMapper {
    AddressDto toDto(Address address);
    Address toEntity(AddressDto addressDto);
}
