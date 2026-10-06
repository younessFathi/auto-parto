package auto.parto.mapper;

import auto.parto.dto.SellerDto;
import auto.parto.persistence.SellerIdentityRecord;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.UUID;

@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface SellerIdentityMapper {

    @Mapping(source = "publicId", target = "id")
    @Mapping(source = "active", target = "isActive")
    @Mapping(target = "name", expression = "java(fullName(identity))")
    SellerDto toDto(SellerIdentityRecord identity);

    default String map(UUID publicId) {
        return publicId == null ? null : publicId.toString();
    }

    default String fullName(SellerIdentityRecord identity) {
        String name = String.join(" ", identity.firstName() == null ? "" : identity.firstName(),
                identity.lastName() == null ? "" : identity.lastName()).trim();
        return name.isBlank() ? identity.email() : name;
    }
}
