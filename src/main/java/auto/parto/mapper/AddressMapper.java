package auto.parto.mapper;

import auto.parto.dto.AddressDto;
import auto.parto.model.Address;

public class AddressMapper {
    public static AddressDto toDto(Address address) {
        return address != null ? AddressDto.builder()
                .street(address.getStreet())
                .city(address.getCity())
                .state(address.getState())
                .country(address.getCountry())
                .zipCode(address.getZipCode())
                .build():null;
    }

    public static Address toEntity(AddressDto addressDto) {
        return addressDto != null ? Address.builder()
                .street(addressDto.getStreet())
                .city(addressDto.getCity())
                .state(addressDto.getState())
                .country(addressDto.getCountry())
                .zipCode(addressDto.getZipCode())
                .build():null;
    }
}
