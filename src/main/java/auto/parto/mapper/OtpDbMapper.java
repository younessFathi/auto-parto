package auto.parto.mapper;

import auto.parto.dto.OtpDetailsDto;
import auto.parto.model.OtpDetails;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface OtpDbMapper {

    OtpDetailsDto toDto(OtpDetails otp);
    OtpDetails toEntity(OtpDetailsDto otpDto);
}
