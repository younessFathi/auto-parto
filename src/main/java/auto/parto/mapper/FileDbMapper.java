package auto.parto.mapper;

import auto.parto.dto.FileDto;
import auto.parto.model.File;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface FileDbMapper {
    FileDto toDto(File file);
    File toEntity(FileDto fileDto);
}
